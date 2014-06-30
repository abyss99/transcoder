package com.campmobile.core.compressvideo;

import android.annotation.SuppressLint;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by abyss on 2014. 6. 9..
 */
@SuppressLint("NewApi")
public class TransCoder implements SurfaceHolder.Callback {
	String TAG = "TransCoder";
	private MediaCodec mediaCodec;
	private BufferedOutputStream mBufferedOutputStream;

	public TransCoder() {

	}

	public void encode(String filePath) {
		Decoder decoder = new Decoder(filePath);
		decoder.start();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {

	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {

	}

	class Decoder extends Thread {
		private String mPath;

		public Decoder(String path) {
			mPath = path;
		}

		@Override
		public void run() {
			try {
				AvcEncoder avcEncoder = new AvcEncoder();
				avcEncoder.offerEncoder(mPath);
				Log.i(TAG, "finish");
				avcEncoder.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	class AvcEncoder {

		private MediaCodec decoder;
		private MediaCodec encoder;
		private BufferedOutputStream outputStream;

		public AvcEncoder() {

			File f = new File(Environment.getExternalStorageDirectory(), "Download/video_encoded.mp4");
			try {

				outputStream = new BufferedOutputStream(new FileOutputStream(f));
			} catch (Exception e) {
				e.printStackTrace();
			}

			//			encoder = MediaCodec.createEncoderByType("video/avc");
			//			MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 320, 240);
			//			mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 125000);
			//			mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
			//			mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, selectColorFormat("video/avc"));
			//			mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
			//			encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			//			encoder.start();
		}

		private MediaCodecInfo selectCodec(String mimeType) {
			int numCodecs = MediaCodecList.getCodecCount();
			for (int i = 0; i < numCodecs; i++) {
				MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

				if (!codecInfo.isEncoder()) {
					continue;
				}

				for (String type : codecInfo.getSupportedTypes()) {
					if (type.equalsIgnoreCase(mimeType)) {
						Log.i("selectCodec", "SelectCodec : " + codecInfo.getName());
						return codecInfo;
					}
				}
			}
			return null;
		}

		protected int selectColorFormat(String mimeType) {
			MediaCodecInfo codecInfo = selectCodec(mimeType);
			if (codecInfo == null) {
				throw new RuntimeException("Unable to find an appropriate codec for " + mimeType);
			}

			MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
			for (int i = 0; i < capabilities.colorFormats.length; i++) {
				int colorFormat = capabilities.colorFormats[i];
				if (isRecognizedFormat(colorFormat)) {
					Log.d("ColorFomar", "Find a good color format for " + codecInfo.getName() + " / " + mimeType);
					return colorFormat;
				}
			}
			return -1;
		}

		private boolean isRecognizedFormat(int colorFormat) {
			switch (colorFormat) {
			// these are the formats we know how to handle for this test
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
				case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
					return true;
				default:
					return false;
			}
		}

		public void close() {
			try {
				decoder.stop();
				decoder.release();
				outputStream.flush();
				outputStream.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public void offerEncoder(String path) {
			int numFramesExtracted = 0;
			int numFramesDecoded = 0;

			MediaExtractor mediaExtractor = new MediaExtractor();
			try {
				mediaExtractor.setDataSource(path);
			} catch (IOException e) {
				e.printStackTrace();
			}

			for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
				MediaFormat format = mediaExtractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				if (mime.equals("video/avc")) {
					mediaExtractor.selectTrack(i);
					decoder = MediaCodec.createDecoderByType(mime);
					format.setInteger(MediaFormat.KEY_WIDTH, 240);
					format.setInteger(MediaFormat.KEY_HEIGHT, 320);
					decoder.configure(format, null, null, 0);
					break;
				}
			}

			decoder.start();

			ByteBuffer[] decodeInputBuffers = decoder.getInputBuffers();
			ByteBuffer[] decodeOutputBuffers = decoder.getOutputBuffers();

			MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
			boolean endOfStream = false;

			while (!Thread.interrupted()) {
				if (!endOfStream) {
					int inputIndex = decoder.dequeueInputBuffer(-1);
					if (inputIndex >= 0) {
						ByteBuffer buffer = decodeInputBuffers[inputIndex];
						int sampleSize = mediaExtractor.readSampleData(buffer, 0);
						if (sampleSize < 0) {
							decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							endOfStream = true;
							Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
						} else {
							numFramesExtracted++;
							decoder.queueInputBuffer(inputIndex, 0, sampleSize, mediaExtractor.getSampleTime(), 0);
							mediaExtractor.advance();
						}
					}
				}

				int outputIndex = decoder.dequeueOutputBuffer(info, 0);
				switch (outputIndex) {
					case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
						Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
						decodeOutputBuffers = decoder.getOutputBuffers();
						break;
					case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
						Log.d(TAG, "New format " + decoder.getOutputFormat());
						break;
					case MediaCodec.INFO_TRY_AGAIN_LATER:
						Log.d(TAG, "dequeueOutputBuffer timed out!");
						break;
					default:
						ByteBuffer buffer = decodeOutputBuffers[outputIndex];

						byte[] outData = new byte[buffer.remaining()];
						buffer.get(outData);
						try {
							outputStream.write(outData);
						} catch (IOException e) {
							e.printStackTrace();
						}
						numFramesDecoded++;
						decoder.releaseOutputBuffer(outputIndex, false);
						break;
				}

				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
					break;
				}
			}

		}
	}
}
