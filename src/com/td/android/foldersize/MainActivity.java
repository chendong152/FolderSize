package com.td.android.foldersize;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.Callback;

import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.td.android.foldersize.AnalyService.AnalyBinder;
import com.td.android.foldersize.AnalyService.ResultItem;
import com.td.android.libs.infra.IToMapConverter;
import com.td.android.libs.infra.IPredicate;
import com.td.android.libs.infra.util.TdListHelper;

import android.R.bool;
import android.R.integer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends /* Sliding */Activity {
	public static final int ORDER_BY_NAME = 1;
	public static final int ORDER_BY_SIZE = 2;

	private static int getFileCount(File file) {
		File[] fsFiles = file.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isFile();
			}
		});
		return fsFiles == null ? 0 : fsFiles.length;
	}

	private static int getFolderCount(File file) {
		File[] fsFiles = file.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});
		return fsFiles == null ? 0 : fsFiles.length;
	}

	private static String fileCountStr(File file) {
		return file.isDirectory() ? getFileCount(file) + "���ļ�" : "";
	}

	private static String folderCountStr(File file) {
		return file.isDirectory() ? getFolderCount(file) + "���ļ���" : "";
	}

	private final class FolderConverter implements IToMapConverter<File, String, Object> {
		@Override
		public Map<String, Object> Convert(File object) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("text", object.getName());
			map.put("file", object);
			map.put("size", 0l);
			map.put("sizeStr", "0.00B");
			map.put("fileCount", fileCountStr(object));
			map.put("folderCount", folderCountStr(object));
			map.put("icon", object.isFile() ? R.drawable.ic_file : R.drawable.ic_launcher);
			return map;
		}
	}

	private AnalyService mAnalyService;
	private ListView mLvFolder;
	private static Handler mSizeHandler;
	private TextView tvTitle;
	private ServiceConnection mServiceConnection;
	private SlidingMenu mSlidingMenu;
	private String mCurrentPath;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		WindowManager wm = getWindowManager();
		DisplayMetrics outMetrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(outMetrics);
		int wdp = (int) (outMetrics.widthPixels * 0.7);

		// �����˵�
		SlidingMenu menu = new SlidingMenu(this);
		menu.setMode(SlidingMenu.LEFT);
		menu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
		menu.setFadeDegree(0.35f);
		menu.attachToActivity(this, SlidingMenu.SLIDING_CONTENT);
		menu.setBehindWidth(wdp);
		menu.setMenu(R.layout.slide_menu_content);

		Log.i("my-debug", "onCreate ");

		mSizeHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				Log.i("my-debug", "Activity �յ�����Ϣ,�Ƿ�����ɣ�" + mAnalyService.IsFinished());
				// �������һ���ļ��д�С
				ResultItem ret = (ResultItem) msg.obj;
				if (mAnalyService.IsFinished()) {
					freshSize(ret);
				} else {
					// TODO:
					if (ret != null)
						Log.i("my-debug", "Activity�a���˽Y����" + ret.path + ret.getSizeString());
					else
						Log.i("my-debug", "Activity ��Ϣ�ڣ���δ��ȡ��������");
				}
			}
		};

		mServiceConnection = new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {
				unbindService(this);
				// mServiceConnection=null;
			}

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mAnalyService = ((AnalyBinder) service).getService();
				mAnalyService.setHandler(mSizeHandler);
				mServiceStarted = true;
				bindFolders("/");
			}
		};
		Intent service = new Intent(getApplicationContext(), AnalyService.class);
		bindService(service, mServiceConnection, BIND_AUTO_CREATE);
	}

	private boolean mServiceStarted;

	@Override
	protected void onStart() {
		super.onStart();

		mLvFolder = (ListView) findViewById(R.id.lvFolder);
		tvTitle = (TextView) findViewById(R.id.tvTitle);

		mSource = mServiceStarted ? mAnalyService.cachedData : new ArrayList<Map<String, Object>>();
		mAdapter = new FileAdapter(MainActivity.this, mSource, R.layout.folder_item, new String[] { "text", "sizeStr",
				"icon", "fileCount", "folderCount" }, new int[] { R.id.tvPath, R.id.tvSize, R.id.ivIcon,
				R.id.tvFileCount, R.id.tvFolderCount });
		mLvFolder.setAdapter(mAdapter);

		if (mServiceStarted)
			mLvFolder.setSelectionFromTop(mAnalyService.Y,mAnalyService.X);

		Log.i("my-debug", "onStart Y:" + (mAnalyService==null?null:mAnalyService.Y )
				+ ",�ļ�����" + (mSource == null ? 0 : mSource.size()));
		bindListener();
	}

	private static List<Map<String, Object>> mSource;
	private BaseAdapter mAdapter;

	private void bindListener() {
		// �����¼���
		mLvFolder.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				@SuppressWarnings("unchecked")
				Map<String, Object> data = (Map<String, Object>) parent.getAdapter().getItem(position);
				File file = (File) data.get("file");
				// ������ļ��У�����
				if (file.isDirectory()) {
					bindFolders(file.getPath());
				} else if (file.isFile()) {
					MainActivity.this.startActivity(FolderHelper.openFile(file));
					Log.i("my-debug", "mSource:" + mSource.size());
				}
			}
		});
		/*
		 * //�����¼� mLvFolder.setLongClickable(true);
		 * mLvFolder.setOnItemLongClickListener(new OnItemLongClickListener() {
		 * public boolean onItemLongClick(AdapterView<?> parent, View view, int
		 * position, long id) { // TODO Auto-generated method stub return false;
		 * } });
		 */
		mLvFolder.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
				menu.add(0, 1, 0, "ɾ��");
			}
		});

		OnCheckedChangeListener listener = new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked)
					sort(buttonView.getTag());
			}
		};
		CompoundButton ck = (CompoundButton) findViewById(R.id.chkByName);
		ck.setOnCheckedChangeListener(listener);
		ck = (CompoundButton) findViewById(R.id.chkBySize);
		ck.setOnCheckedChangeListener(listener);
	}

	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	private void bindFolders(String path) {
		mCurrentPath = path;
		tvTitle.setText(path);
		mAnalyService.stop();// ��ֹ��ǰ�ļ���

		final Handler handler = new Handler() {
			public void handleMessage(Message msg) {
				List<Map<String, Object>> source = (List<Map<String, Object>>) ((Object[]) msg.obj)[1];
				File[] children = (File[]) ((Object[]) msg.obj)[0];
				// ��������գ�����ᵼ�¼�����ļ��д�Сˢ��UIʱ������list���޸�list��ͻ
				mSource.clear();
				mSource.addAll(source);
				((BaseAdapter) mLvFolder.getAdapter()).notifyDataSetChanged();

				if (children != null) {
					for (File f : children) {
						mAnalyService.begin(f.getPath(), false);
					}
				}
				super.handleMessage(msg);
			}
		};
		new Thread() {
			public void run() {
				File parent = new File(mCurrentPath);
				File[] children = parent.listFiles();
				FolderConverter converter = new FolderConverter();
				List<Map<String, Object>> source = TdListHelper.Foreach(children, converter);
				if (parent.getParent() != null) {
					Map<String, Object> parentMap = converter.Convert(parent.getParentFile());
					parentMap.put("text", getString(R.string.parentFolderText));
					parentMap.put("size", null);
					parentMap.put("sizeStr", "");
					parentMap.put("fileCount", "");
					parentMap.put("folderCount", "");
					source.add(0, parentMap);
				}
				Message message = new Message();
				message.obj = new Object[] { children, source };
				handler.sendMessage(message);
				super.run();
			}

		}.start();

	}

	private void freshSize(ResultItem ret) {
		if (mSource == null)
			return;

		Log.i("my-debug", "�յ��������ˢ��UI����" + Thread.currentThread().getId() + "");
		List<Map<String, Object>> source = mSource;
		final ResultItem item = ret;
		Map<String, Object> map = TdListHelper.firstOrDefault(source, new IPredicate<Map<String, Object>>() {
			@Override
			public boolean Select(Map<String, Object> map) {
				// Log.i("my-debug", "linq:map:" + map + ",item:" +
				// item);
				return ((File) map.get("file")).getPath().equals(item.path);
			}
		});
		if (map != null) {
			map.put("size", item.size);
			map.put("sizeStr", item.getSizeString());
			// map.put("fileCount", item.getfileCountString());
			// map.put("folderCount", item.getfolderCountString());
			((BaseAdapter) mLvFolder.getAdapter()).notifyDataSetChanged();
		}

		// һ�μ��������ɺ������򣬼��ٲ���Ҫ�Ĵ�������
		if (item.isFinal)
			sort(mSort);
	}

	private Object mSort;

	private synchronized void sort(Object type) {
		mSort = type;
		if (mSort == null || mSource == null || mSource.size() > 200)
			return;

		final Handler handler = new Handler() {
			public void handleMessage(Message msg) {
				((BaseAdapter) mLvFolder.getAdapter()).notifyDataSetChanged();

				super.handleMessage(msg);
			}
		};
		new Thread() {
			public void run() {
				List<Map<String, Object>> source = mSource;
				Collections.sort(source, new FileComparator(mSort));
				handler.sendEmptyMessage(0);
				super.run();
			}
		}.start();
	}

	private boolean backParent() {
		String p = new File(mCurrentPath).getParent();
		if (p == null || p == "")
			return false;
		bindFolders(p);
		return true;
	}

	// �����˵���Ӧ����
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

		if (item.getGroupId() == 0) {
			Map<String, Object> map = (Map<String, Object>) mLvFolder.getAdapter().getItem(info.position);// �����info.id��Ӧ�ľ������ݿ���_id��ֵ

			switch (item.getItemId()) {
			case 1:
				// ɾ������
				deleteFile((File) map.get("file"));
				break;
			default:
				break;
			}
		}

		return super.onContextItemSelected(item);

	}

	/**
	 * ɾ���ļ�
	 * 
	 * @param file
	 * @return
	 */
	private void deleteFile(File file) {
		final File file2 = file;
		new AlertDialog.Builder(this).setTitle("ȷ��ɾ��").setMessage("ȷ��ɾ����·����" + file.getPath())
				.setPositiveButton("��", new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog pDialog = ProgressDialog.show(MainActivity.this, "ִ����", "ִ���У����Ժ�...");
						final Handler handler = new Handler() {
							public void handleMessage(Message msg) {
								super.handleMessage(msg);
								((BaseAdapter) mLvFolder.getAdapter()).notifyDataSetChanged();
								pDialog.dismiss();

								Toast.makeText(MainActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
							}
						};
						new Thread() {
							public void run() {
								super.run();
								boolean r = FolderHelper.recursionDeleteFile(file2);
								String msg = "";
								if (r) {
									msg = "��ɾ����";
									List<Map<String, Object>> source = mSource;
									final File item = file2;
									Map<String, Object> map = TdListHelper.firstOrDefault(source,
											new IPredicate<Map<String, Object>>() {
												@Override
												public boolean Select(Map<String, Object> map) {
													// Log.i("my-debug",
													// "linq:map:" +
													// map + ",item:" +
													// item);
													return ((File) map.get("file")).getPath().equals(item.getPath());
												}
											});
									source.remove(map);
								} else {
									msg = "ɾ��ʧ�ܣ�";
								}
								msg = msg + file2.getPath();
								Message message = new Message();
								message.arg1 = r ? 0 : 1;
								message.obj = msg;
								handler.sendMessage(message);
							}
						}.start();
					}
				}).setNegativeButton("��", null).show();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			boolean r = backParent();
			if (r)
				return r;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onStop() {
		super.onStop();
		mAnalyService.X = 0;
		mAnalyService.Y = mLvFolder.getFirstVisiblePosition();
		mAnalyService.cachedData = mSource;// ���浱ǰ�б�
		Log.i("my-debug", "onStop y:" + mAnalyService.Y);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i("my-debug", "onDestroy ");
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
		// getMenuInflater().inflate(R.menu.main, menu);
		return false;
	}
}
