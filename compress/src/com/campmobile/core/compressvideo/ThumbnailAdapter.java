package com.campmobile.core.compressvideo;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Created by abyss on 2014. 6. 9..
 */
public class ThumbnailAdapter extends BaseAdapter {
	private LayoutInflater mLayoutInflater;
	private List<MainActivity.VideoThumb> mVideoThumbList;

	public ThumbnailAdapter(Context context, List<MainActivity.VideoThumb> videoList) {
		mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mVideoThumbList = videoList;
	}

	@Override
	public int getCount() {
		return mVideoThumbList.size();
	}

	@Override
	public Object getItem(int position) {
		return mVideoThumbList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if (convertView == null) {
			view = mLayoutInflater.inflate(R.layout.item, null);
		}

		ImageView image = (ImageView)view.findViewById(R.id.img_thumb);
		image.setImageBitmap(mVideoThumbList.get(position).getThumbnail());
		TextView text = (TextView)view.findViewById(R.id.txt_label);
		text.setText(mVideoThumbList.get(position).getTitle());

		return view;
	}
}
