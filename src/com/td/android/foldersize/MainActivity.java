package com.td.android.foldersize;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Array;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.jeremyfeinstein.slidingmenu.lib.app.SlidingActivity;
import com.jeremyfeinstein.slidingmenu.lib.app.SlidingFragmentActivity;
import com.td.android.foldersize.AnalyService.AnalyBinder;
import com.td.android.foldersize.AnalyService.ResultItem;
import com.td.android.libs.infra.IToMapConverter;
import com.td.android.libs.infra.IPredicate;
import com.td.android.libs.infra.util.TdListHelper;

import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore.Files;
import android.R.integer;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView.FindListener;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class MainActivity extends /*Sliding*/Activity  {
	public static final int ORDER_BY_NAME=1;
	public static final int ORDER_BY_SIZE=2;
	private static int getFileCount(File file) {
		File[]	fsFiles=file.listFiles(new FileFilter() { 
			public boolean accept(File pathname) {
				return pathname.isFile();
			}
		});
		return fsFiles==null?0:fsFiles.length;
	}
	
	private static int getFolderCount(File file) {
		File[]	fsFiles=file.listFiles(new FileFilter() { 
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});
		return fsFiles==null?0:fsFiles.length;
	}
	private static String fileCountStr(File file) {
		return file.isDirectory()?getFileCount(file)+"个文件":"";
	}
	private static String folderCountStr(File file) {
		return file.isDirectory()?getFolderCount(file)+"个文件夹":"";
	}	
	
	private final class FolderConverter implements IToMapConverter<File, String, Object> {
		public Map<String, Object> Convert(File object) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("text", object.getName());
			map.put("file", object);
			map.put("size", null);
			map.put("sizeStr", "0.00B");
			map.put("fileCount",fileCountStr(object));
			map.put("folderCount",folderCountStr(object));
			map.put("icon", object.isFile()? R.drawable.ic_file:R.drawable.ic_launcher);
			return map;
		}
	}

	private AnalyService mAnalyService;
	private ListView mLvFolder;
	private static Handler mSizeHandler;
	private TextView tvTitle;
	private ServiceConnection mServiceConnection;
	private SlidingMenu mSlidingMenu;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		WindowManager wm=getWindowManager();
		DisplayMetrics outMetrics=new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(outMetrics);
		int wdp=(int)(outMetrics.widthPixels/outMetrics.density*0.7); 
		
		//setBehindContentView(R.layout.slide_menu_content);
		
		//滑动菜单
		//mSlidingMenu=getSlidingMenu();
		//mSlidingMenu.setMode(SlidingMenu.LEFT);
		//mSlidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
		SlidingMenu menu = new SlidingMenu(this);
        menu.setMode(SlidingMenu.LEFT);
        menu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
        //menu.setShadowWidthRes(R.dimen.shadow_width);
        //menu.setShadowDrawable(R.drawable.shadow);
        //menu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
        menu.setFadeDegree(0.35f);
        menu.attachToActivity(this, SlidingMenu.SLIDING_CONTENT);
        menu.setBehindWidth(wdp);
        menu.setMenu(R.layout.slide_menu_content);
		
		Log.i("my-debug","onCreate ");
		
		mSizeHandler = new Handler() {
			public void handleMessage(Message msg) {
				Log.i("my-debug", "Activity 收到了消息,是否已完成：" + mAnalyService.IsFinished());
				// 计算完成一个文件夹大小
				ResultItem ret = (ResultItem) msg.obj;
				if (mAnalyService.IsFinished()) {
					freshSize(ret);
				} else {
					// TODO:
					if (ret != null)
						Log.i("my-debug", "Activity產生了結果：" + ret.path + ret.getSizeString());
					else
						Log.i("my-debug", "Activity 消息内，但未能取到运算结果");
				}
			}
		};

		mServiceConnection = new ServiceConnection() {
			public void onServiceDisconnected(ComponentName name) {
				unbindService(this);
				//mServiceConnection=null;
			}

			public void onServiceConnected(ComponentName name, IBinder service) {
				mAnalyService = (AnalyService) ((AnalyBinder) service).getService();
				mAnalyService.setHandler(mSizeHandler);
				bindFolders("/");
			}
		};
		Intent service = new Intent(getApplicationContext(), AnalyService.class);
		bindService(service, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.i("my-debug","onStart ");
		mLvFolder = (ListView) findViewById(R.id.lvFolder);
		tvTitle = (TextView) findViewById(R.id.tvTitle);
		bindListener();
	}

	private void bindFolders(String path) {
		tvTitle.setText(path);
		mAnalyService.stop();// 中止以前的计算

		File parent = new File(path);
		File[] children = parent.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return true;// pathname.isDirectory();
			}
		});

		if (children != null) {
			// 开始计算大小
			for (File f : children) {
				mAnalyService.begin(f.getPath(), false);
			}
		}

		FolderConverter converter = new FolderConverter();
		List<Map<String, Object>> source = TdListHelper.Foreach(children, converter);
		if (parent.getParent() != null) {
			Map<String, Object> parentMap = converter.Convert(parent.getParentFile());
			parentMap.put("text", getString(R.string.parentFolderText));
			parentMap.put("sizeStr", "");
			parentMap.put("fileCount","");
			parentMap.put("folderCount","");
			source.add(0, parentMap);
		}

		mLvFolder.setTag(source);// 记录source
		ListAdapter adapter = new FileAdapter(this, source, R.layout.folder_item, new String[] { "text", "sizeStr","icon","fileCount","folderCount" },
				new int[] { R.id.tvPath, R.id.tvSize ,R.id.ivIcon,R.id.tvFileCount,R.id.tvFolderCount});
		mLvFolder.setAdapter(adapter);

		// 单击事件。
		mLvFolder.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				@SuppressWarnings("unchecked")
				Map<String, Object> data = (Map<String, Object>) parent.getAdapter().getItem(position);
				File file = (File) data.get("file");
				// 如果是文件夹，当绑定
				if (file.isDirectory())
					bindFolders(file.getPath());
			}
		});
	}

	private void freshSize(ResultItem ret) {
		Log.i("my-debug", "收到计算完成刷新UI请求：" + Thread.currentThread().getId() + "");
		List<Map<String, Object>> source = (List<Map<String, Object>>) mLvFolder.getTag();
		final ResultItem item = ret;
		Map<String, Object> map = TdListHelper.firstOrDefault((List<Map<String, Object>>) source,
				new IPredicate<Map<String, Object>>() {
					public boolean Select(Map<String, Object> map) {
						//Log.i("my-debug", "linq:map:" + map + ",item:" + item);
						return ((File) map.get("file")).getPath().equals(item.path);
					}
				});
		if (map != null) {
			map.put("size", item.size);
			map.put("sizeStr", item.getSizeString());
			//map.put("fileCount", item.getfileCountString());
			//map.put("folderCount", item.getfolderCountString());
			((BaseAdapter) mLvFolder.getAdapter()).notifyDataSetChanged();
		}
		
		//一次计算队列完成后再排序，减少不必要的大量排序
		if(item.isFinal)
			sort(mSort);
	}
	
	private Object mSort;
	private void sort(Object type) {
		mSort=type;
		if(mSort==null)
			return;
		
		List<Map<String, Object>> source = (List<Map<String, Object>>) mLvFolder.getTag();
		Collections.sort(source,new FileComparator(mSort));
		((BaseAdapter) mLvFolder.getAdapter()).notifyDataSetChanged();
	}

	private void bindListener() {
		OnCheckedChangeListener listener=new OnCheckedChangeListener() { 
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) { 
				if(isChecked) sort(buttonView.getTag());
			}
		};
		CompoundButton ck=(CompoundButton)findViewById(R.id.chkByName);
		ck.setOnCheckedChangeListener(listener);
		ck=(CompoundButton)findViewById(R.id.chkBySize);
		ck.setOnCheckedChangeListener(listener);
	}
	 

	protected void onStop() { 
		super.onStop();
		Log.i("my-debug","onStop ");
	}
	
	protected void onDestroy() {
		super.onDestroy();
		Log.i("my-debug","onDestroy ");
		mServiceConnection.onServiceDisconnected(new ComponentName(getApplicationContext(), AnalyService.class));
	}
	
	@Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		super.onRestart();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public class FileAdapter extends SimpleAdapter {

		public FileAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
			super(context, data, resource, from, to);
		}
		

		@Override
		public void setViewImage(ImageView v, int value) {
			// TODO Auto-generated method stub
			super.setViewImage(v, value);
		}


		public View getView(int position, View convertView, ViewGroup parent) {
			View ret = super.getView(position, convertView, parent);
			/*TextView tv = (TextView) findViewById(R.id.tvPath);
			if(tv!=null) {
				File file = (File) ((Map<String, Object>) getItem(position)).get("file");
				Drawable drawable = getResources().getDrawable(file.isFile() ? R.drawable.ic_file : R.drawable.ic_launcher);
				drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight()); // 这一步必须要做,否则不会显示.
				Log.i("my-debug",file+",isFile:"+file.isFile());
				tv.setCompoundDrawables(drawable, null, null, null);
			}*/
			return ret;
		}

	}
	
	private class FileComparator implements Comparator<Map<String, Object>> {
		private int mSortType;
		public FileComparator(Object sort) {
			if(sort.getClass()==String.class) sort=Integer.parseInt((String)sort);
			mSortType= (Integer) sort;
		}
		private int fileTypeCompare(Map<String, Object> lhs, Map<String, Object> rhs) {
			File lFile=(File)lhs.get("file");
			File rFile=(File)rhs.get("file");
			int l=lFile.isDirectory()?0:1;
			int r=rFile.isDirectory()?0:1;
			return l-r;
		}
		
		public int compare(Map<String, Object> lhs, Map<String, Object> rhs) {
			int t=fileTypeCompare(lhs, rhs);
			if(t!=0) return t;
			switch (mSortType) {
			case MainActivity.ORDER_BY_NAME:
				String l=(String)lhs.get("text");
				String r=(String)rhs.get("text");
				if(l==null) return -1;
				if(r==null) return 1;
				return l.compareTo(r);
				
			case MainActivity.ORDER_BY_SIZE:
				Object li=lhs.get("size");
				Object ri=rhs.get("size");
				if(li==null) return 1;
				if(ri==null) return 1;
				Log.i("my-debug","比较：li:"+li+",ri:"+ri+",result:"+((Long)ri).compareTo((Long)li));
				return ((Long)ri).compareTo((Long)li);

			default:
				break;
			}
			return 0;
		} 
	}
}
