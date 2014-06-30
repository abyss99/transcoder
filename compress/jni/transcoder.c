#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>

#include <jni.h>

/*for android logs*/
#include <android/log.h>

#define LOG_TAG "android-ffmpeg-tutorial01"
#define LOGI(...) __android_log_print(4, LOG_TAG, __VA_ARGS__);
#define LOGE(...) __android_log_print(6, LOG_TAG, __VA_ARGS__);

static void log_packet(const AVFormatContext *fmt_ctx, const AVPacket *pkt, const char *tag) {
    AVRational *time_base = &fmt_ctx->streams[pkt->stream_index]->time_base;
    LOGI("%s: pts:%s pts_time:%s dts:%s dts_time:%s duration:%s duration_time:%s stream_index:%d\n",
           tag, av_ts2str(pkt->pts), av_ts2timestr(pkt->pts, time_base), av_ts2str(pkt->dts), av_ts2timestr(pkt->dts, time_base),
           av_ts2str(pkt->duration), av_ts2timestr(pkt->duration, time_base), pkt->stream_index);
}


jint transMain(JNIEnv *pEnv, jclass pObj, jstring input, jstring output) {

    char* in_filename = (char*)(*pEnv)->GetStringUTFChars(pEnv, input, NULL);
    char* out_filename = (char *)(*pEnv)->GetStringUTFChars(pEnv, output, NULL);
    AVCodecContext *enc_ctx, *dec_ctx;
    AVCodecContext *audio_enc_ctx, *audio_dec_ctx;

    AVOutputFormat *outputFormat = NULL;
    AVFormatContext *inputFormat_ctx = NULL, *outputFormat_ctx = NULL;


    int ret, i;

    avcodec_register_all();
    av_register_all();

    if ((ret = avformat_open_input(&inputFormat_ctx, in_filename, 0, 0)) < 0) {
        LOGI("Could not open input file '%s'", in_filename);
        goto end;
    }

    if ((ret = avformat_find_stream_info(inputFormat_ctx, 0)) < 0) {
        LOGI("Failed to retrieve input stream information");
        goto end;
    }
    av_dump_format(inputFormat_ctx, 0, in_filename, 0);
    avformat_alloc_output_context2(&outputFormat_ctx, NULL, NULL, out_filename);
    if (!outputFormat_ctx) {
        LOGI("Could not create output context\n");
        ret = AVERROR_UNKNOWN;
        goto end;
    }
    outputFormat = outputFormat_ctx->oformat;

 AVDictionaryEntry *tag = NULL;


    for (i = 0; i < inputFormat_ctx->nb_streams; i++) {
        AVStream *in_stream = inputFormat_ctx->streams[i];


        while ((tag = av_dict_get(in_stream->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
                LOGI("%s=%s\n", tag->key, tag->value);
        }



        AVStream *out_stream;

        if(in_stream->codec->codec_type == AVMEDIA_TYPE_VIDEO) {

            dec_ctx = in_stream->codec;

            AVCodec *encoder = avcodec_find_encoder(AV_CODEC_ID_H263);
            out_stream = avformat_new_stream(outputFormat_ctx, NULL);
            enc_ctx = out_stream->codec;

            enc_ctx->height = 576;
            enc_ctx->width = 704;

            enc_ctx->sample_aspect_ratio = dec_ctx->sample_aspect_ratio;
            enc_ctx->pix_fmt = encoder->pix_fmts[0];
//            enc_ctx->time_base = dec_ctx->time_base;
            LOGI("BIT RATE %d", dec_ctx->bit_rate);
            LOGI("den %d", dec_ctx->time_base.den);
            LOGI("num %d", dec_ctx->time_base.num);

            enc_ctx->bit_rate = dec_ctx->bit_rate;
            enc_ctx->time_base.den = dec_ctx->time_base.den;
            enc_ctx->time_base.num = dec_ctx->time_base.num;
//            enc_ctx->time_base.den = 25;
//            enc_ctx->time_base.num = 1;
//            enc_ctx->gop_size = 12;
//            enc_ctx->bit_rate = 30000000;

            avcodec_open2(enc_ctx, encoder, NULL);

            AVCodec *codec = avcodec_find_decoder(dec_ctx->codec_id);
            avcodec_open2(dec_ctx, codec, NULL);
        } else if(in_stream->codec->codec_type == AVMEDIA_TYPE_AUDIO) {
            audio_dec_ctx = in_stream->codec;

            AVCodec *encoder = avcodec_find_encoder(AV_CODEC_ID_AAC);

            out_stream = avformat_new_stream(outputFormat_ctx, NULL);
            audio_enc_ctx = out_stream->codec;

            audio_enc_ctx->channels = audio_dec_ctx->channels;
            audio_enc_ctx->channel_layout = audio_dec_ctx->channel_layout;
            audio_enc_ctx->sample_rate = audio_dec_ctx->sample_rate;
            audio_enc_ctx->sample_fmt = audio_dec_ctx->sample_fmt;
            audio_enc_ctx->bit_rate = audio_dec_ctx->bit_rate;
            audio_enc_ctx->strict_std_compliance = -2;
            audio_enc_ctx->time_base = audio_dec_ctx->time_base;

            avcodec_open2(audio_enc_ctx, encoder, NULL);

            AVCodec *codec = avcodec_find_decoder(audio_dec_ctx->codec_id);
            avcodec_open2(audio_dec_ctx, codec, NULL);

        } else {
            LOGI("What?");
        }


        out_stream->codec->codec_tag = 0;
        if (outputFormat_ctx->oformat->flags & AVFMT_GLOBALHEADER) {
            out_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;
        }
    }
    av_dump_format(outputFormat_ctx, 0, out_filename, 1);
    if (!(outputFormat->flags & AVFMT_NOFILE)) {
        ret = avio_open(&outputFormat_ctx->pb, out_filename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            LOGI("Could not open output file '%s'", out_filename);
            goto end;
        }
    }
    ret = avformat_write_header(outputFormat_ctx, NULL);
    if (ret < 0) {
        LOGI("Error occurred when opening output file\n");
        goto end;
    }


    int got_frame, got_output;
//    AVFrame *frame = av_frame_alloc();
//    AVFrame *frame2 = av_frame_alloc();

    AVPacket packet, outPacket;

    struct SwsContext *context;
    context = sws_getContext(dec_ctx->width, dec_ctx->height, dec_ctx->pix_fmt, enc_ctx->width, enc_ctx->height, AV_PIX_FMT_YUV420P, SWS_LANCZOS, NULL, NULL, NULL);

    AVFrame *frame = av_frame_alloc();
    AVFrame * frame2 = av_frame_alloc();
    avpicture_alloc((AVPicture *)frame2, AV_PIX_FMT_YUV420P, enc_ctx->width, enc_ctx->height);

    if(context == NULL) {
        LOGI("context is null\n");
        goto end;
    }
    while (1) {
        ret = av_read_frame(inputFormat_ctx, &packet);
        if (ret < 0) {
            break;
        }

        AVStream *in_stream  = inputFormat_ctx->streams[packet.stream_index];
        AVStream *out_stream = outputFormat_ctx->streams[packet.stream_index];

        log_packet(inputFormat_ctx, &packet, "in");

        if(inputFormat_ctx->streams[packet.stream_index]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {

            packet.dts = av_rescale_q_rnd(packet.dts, in_stream->time_base, in_stream->codec->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
            packet.pts = av_rescale_q_rnd(packet.pts, in_stream->time_base, in_stream->codec->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
            packet.duration = av_rescale_q(packet.duration, in_stream->codec->time_base, in_stream->time_base);


            ret = avcodec_decode_video2(dec_ctx, frame, &got_frame, &packet);

            av_free_packet(&packet);
            packet.data = NULL;
            packet.size = 0;
            if(ret < 0) {
                av_frame_free(&frame);
                LOGI("Video decoding failed\n");
                break;
            }

            if (got_frame) {
                outPacket.data = NULL;
                outPacket.size = 0;
                av_init_packet(&outPacket);

                ret = sws_scale(context, (const uint8_t * const *) frame->data, frame->linesize, 0, dec_ctx->height, frame2->data, frame2->linesize);

                frame2->pts = av_frame_get_best_effort_timestamp(frame);
                ret = avcodec_encode_video2(enc_ctx, &outPacket, frame2, &got_output);

                if(ret < 0) {
                    LOGI("Video encoding failed\n");
                    break;
                }

                if(got_output) {
                    outPacket.stream_index = packet.stream_index;
                    outPacket.dts = av_rescale_q_rnd(outPacket.dts, out_stream->codec->time_base, out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
                    outPacket.pts = av_rescale_q_rnd(outPacket.pts, out_stream->codec->time_base, out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
                    outPacket.duration = av_rescale_q(outPacket.duration, out_stream->codec->time_base, out_stream->time_base);

                    ret = av_interleaved_write_frame(outputFormat_ctx, &outPacket);

                    if(ret < 0) {
                        LOGI("Audio write failed\n");
                    }
                    av_free_packet(&outPacket);
                }

            }


        } else if(inputFormat_ctx->streams[packet.stream_index]->codec->codec_type == AVMEDIA_TYPE_AUDIO) {

            packet.dts = av_rescale_q_rnd(packet.dts, in_stream->time_base, in_stream->codec->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
            packet.pts = av_rescale_q_rnd(packet.pts, in_stream->time_base, in_stream->codec->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);

            ret = avcodec_decode_audio4(audio_dec_ctx, frame, &got_frame, &packet);
            av_free_packet(&packet);
            packet.data = NULL;
            packet.size = 0;
            if(ret < 0) {
                av_frame_free(&frame);
                LOGI("Audio decoding failed\n");
                break;
            }
            if (got_frame) {
                outPacket.data = NULL;
                outPacket.size = 0;
                av_init_packet(&outPacket);

                ret = avcodec_encode_audio2(audio_enc_ctx, &outPacket, frame, &got_output);
//                av_frame_free(&frame);
                if(ret < 0) {
                    LOGI("fail audio encoding\n");
                    break;
                }

                if(got_output) {
                    outPacket.stream_index = packet.stream_index;
                    outPacket.dts = av_rescale_q_rnd(outPacket.dts, out_stream->codec->time_base, out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
                    outPacket.pts = av_rescale_q_rnd(outPacket.pts, out_stream->codec->time_base, out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
                    outPacket.duration = av_rescale_q(outPacket.duration, out_stream->codec->time_base, out_stream->time_base);
                    ret = av_interleaved_write_frame(outputFormat_ctx, &outPacket);
                    if(ret < 0) {
                        LOGI("Audio write failed\n");
                    }
                    av_free_packet(&outPacket);
                }
            }
//            av_frame_free(&frame);
        }



        log_packet(outputFormat_ctx, &packet, "out");


        if (ret < 0) {
            LOGI("Error muxing packet\n");
            break;
        }
        av_free_packet(&packet);
    }


    av_frame_free(&frame2);
    sws_freeContext(context);
    av_frame_free(&frame);

    av_write_trailer(outputFormat_ctx);
end:
    avformat_close_input(&inputFormat_ctx);
    /* close output */
    if (outputFormat_ctx && !(outputFormat->flags & AVFMT_NOFILE)) {
        avio_close(outputFormat_ctx->pb);
    }

    avformat_free_context(outputFormat_ctx);
    if (ret < 0 && ret != AVERROR_EOF) {
        LOGI("Error occurred: %s\n", av_err2str(ret));
        return 1;
    }
    return 0;
}

jint JNI_OnLoad(JavaVM* pVm, void* reserved) {
	JNIEnv* env;
	if ((*pVm)->GetEnv(pVm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
		 return -1;
	}
	JNINativeMethod nm[1];
	nm[0].name = "transMain";
	nm[0].signature = "(Ljava/lang/String;Ljava/lang/String;)I";
	nm[0].fnPtr = (void*)transMain;
	jclass cls = (*env)->FindClass(env, "com/campmobile/core/compressvideo/MainActivity");
	//Register methods with env->RegisterNatives.
	(*env)->RegisterNatives(env, cls, nm, 1);
	return JNI_VERSION_1_6;
}