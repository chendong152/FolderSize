package com.td.android.foldersize;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import android.R.string;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class FileAdapter extends SimpleAdapter {

	public boolean showThumbnail;
	
	private Context mContext;

	public FileAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
		super(context, data, resource, from, to);
		mContext = context;
	}

	@Override
	public void setViewImage(ImageView v, int value) {
		// TODO Auto-generated method stub
		super.setViewImage(v, value);
	}

	private FileInputStream mFileInputStream;
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View ret = super.getView(position, convertView, parent);
		Map<String, Object> map = (Map<String, Object>) getItem(position);
		File file = (File) map.get("file");
		Object size = map.get("size");
		long criticalSize = 100 * 1024 * 1024;// 100M
		TextView tv = (TextView) ret.findViewById(R.id.tvPath);
		if (tv != null) {
			String sd = "/sdcard";// +Environment.getExternalStorageDirectory().getName();
			tv.setTextAppearance(mContext,
					size != null && (Long) size >= criticalSize && file.getPath().startsWith(sd) ? R.style.stLightPath
							: R.style.stNormalPath);
		}
		ImageView iView = (ImageView) ret.findViewById(R.id.ivIcon);
		if (showThumbnail && iView != null && file.isFile() && FolderHelper.isImg(file.getPath())) {
			BitmapFactory.Options options=new BitmapFactory.Options();
			options.inJustDecodeBounds=true;
			Bitmap bitmap=null;
			try {
				mFileInputStream=new FileInputStream(file);
				BitmapFactory.decodeStream(mFileInputStream,null,options); 
				mFileInputStream.close();
				mFileInputStream=null;
				int scale = Math.max(1,options.outWidth/60);
				options.inSampleSize=scale;
				options.inJustDecodeBounds=false;
				mFileInputStream=new FileInputStream(file);
				bitmap = BitmapFactory.decodeStream(mFileInputStream,null,options);
				mFileInputStream.close();
				mFileInputStream=null;
				Log.i("my-debug","file:"+file.getPath()+",width:"+options.outWidth+",scale:"+scale+",bitmap:"+bitmap);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace(); 
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (bitmap != null) {
				iView.setImageBitmap(bitmap);
				//bitmap.recycle();
				bitmap=null;
				//iView.setBackground(Drawable.createFromPath(file.getPath()));
			}
		}
		return ret;
	}

}
