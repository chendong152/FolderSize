/*
 * Author:	Dong [mailto:techdong@hotmail.com]
 * Date:	����11:55:40
 */
package com.td.android.foldersize;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;

import android.R.bool;
import android.R.integer;
import android.app.Service;
import android.content.ClipData.Item;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Message;
import android.provider.MediaStore.Files;
import android.text.TextUtils.StringSplitter;
import android.util.Log;

import com.td.android.libs.infra.util.*;

/**
 * @author dong
 * 
 */
public class AnalyService extends Service {
	private AnalyBinder mBinder;
	private Handler mHandler;// �첽���㣬������ɺ�ͨ��hander.sendMessage֪ͨ
	private FolderHelper.CancelToken mToken;
	private boolean mIsFinished;

	private volatile long mTotalSize;
	private ResultItem mResultItem;
	
	private synchronized void addSize(long singleSize) {
		mTotalSize += singleSize;
	}
	
	private synchronized void addResultItem(ResultItem item) {
		if(mResultItem==null) {
			mResultItem=item;
		}
		else {
			mResultItem.size+=item.size;
			mResultItem.fileCount+=item.fileCount;
			mResultItem.folderCount+=item.folderCount;
		}
	}

	/**
	 * �Ƿ������ɡ�
	 * 
	 * @return Ϊtrue��ʾ����������ɣ�Ϊfalse��ʾ����ֹ��
	 */
	public boolean IsFinished() {
		return mIsFinished;
	}

	/**
	 * 
	 * @param handler
	 *            ����2ָ���Ƿ���ɼ��㡣
	 */
	public AnalyService() {
		mToken = new FolderHelper.CancelToken();
		mBinder = new AnalyBinder();
	}

	public void setHandler(Handler handler) {
		mHandler = handler;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private String mPath;
	private int mCompletedThreadCount = 0;// ����ɵ��߳��������ָ�߳���ֹͣ������ָ������ս���ļ��㡣

	private LinkedList<String> mPathQueue = new LinkedList<String>();// ������С����е�·��ΪҪ�����·��
	private LinkedList<ResultItem> mResultQueue = new LinkedList<ResultItem>();// ������С����е�·��Ϊ����ɼ����·����

	/*public ResultItem getResutItem() {
		return mResultQueue.poll();// ��������ͷ��·��
	}*/

	public void stop() {
		mPathQueue.clear();
		mToken.canceled = true;
	}

	/**
	 * ��ʼ����ָ���ļ��еĴ�С
	 * 
	 * @param path
	 * @throws InterruptedException
	 */
	public void begin(String path, boolean clearOld) {
		Log.i("my-debug", "Begin path:" + path + ",  isRunning:" + isRunning()+", ��һ���ļ���"+mPath);
		if (path == null) {
			return;
		}

		String[] excludedStrings=new String[] {"/proc","/sys"};
		boolean matched=false;
		for(String s:excludedStrings) {
			if((matched=path.startsWith(s))) break;
		}
		if(matched)
		{
			return;
		}
		
		// �������Ӌ��ָ���ļ��A,�^�mӋ�㡣
		if (mPath != null && mPath.equals(path)) {
			if (clearOld) {
				mPathQueue.clear();// �����ǰ���е�ͬʱ
			}
			return;
		}else {
			if (clearOld) {
				stop();// �����ǰ���е�ͬʱ����ֹ��ǰ�ļ���
			}
		}

		// ָ���ļ��A�ѱ���ӵ���У������ƶ���ͷ
		if (mPathQueue.contains(path)) {
			mPathQueue.remove(path);
			mPathQueue.addFirst(path);
		}

		mPathQueue.add(path);

		if (!isRunning())// �����ǰ���ڼ����У���ʼ���㡣
			calc();
	}

	/**
	 * @param path
	 */
	private synchronized void calc() {
		mToken.setCanceld(false);// ��¼��ʶΪ��δ��ֹ��
		if (mPathQueue.isEmpty())
			return;

		String path = mPathQueue.poll();

		Log.i("my-debug", "������calc,��ǰ���������߳�����" + mStartedThreadCount + "�� ����ɵ��߳�����" + mCompletedThreadCount
				+ ", ��������path:" + path);

		mPath = path;
		mTotalSize = 0;

		Callback finishCallback = new Callback() {
			public void onFinish(Object obj) {
				AsyncThread r = (AsyncThread) obj;
				onItemComplete(r);
			}
		};

		// ��ʼ3���߳���ִ�м���,�ֱ�ִ��ָ�������ļ���
		File file = new File(mPath);
		if (file.isDirectory()) {
			File[] allFolders = file.listFiles(new FileFilter() {
				public boolean accept(File pathname) {
					return pathname.isDirectory();
				}
			});
			if (allFolders != null && allFolders.length > 0) {
				mIsRunning = true;// ֻ�е��������̺߳�Ż���롰ִ���С�
				int count = allFolders.length / 3;
				int mod = allFolders.length % 3;
				int start = 0;
				for (int i = 0; i < 3; i++) {
					mStartedThreadCount++;
					int end = start + count + (mod > i ? 1 : 0);
					File[] files = TdHelper.copyOfRange(allFolders, start, end);
					start = end;
					new AsyncThread(files, finishCallback).start();
				}
			}

			// 1���߳�ִ�е�ǰ�ļ����µ��ļ����ܴ�С
			File[] allFiles = file.listFiles(new FileFilter() {
				public boolean accept(File pathname) {
					return pathname.isFile();
				}
			});
			if (allFiles != null && allFiles.length > 0) {
				mIsRunning = true;// ֻ�е��������̺߳�Ż���롰ִ���С�
				mStartedThreadCount++;
				AsyncThread thread = new AsyncThread(allFiles, finishCallback);
				thread.start();
			}
			
			//����ǿ��ļ��У�û���κ����ļ����ļ��У���ֱ�ӻ�ý��
			if (!mIsRunning) {
				ResultItem item=new ResultItem();
				addResultItem(item);
			}
		} else if (file.isFile()) {
			mTotalSize = file.length();
			ResultItem item=new ResultItem();
			item.size=file.length();
			item.fileCount=1;
			addResultItem(item);
		}else/*�����ڣ�ʵ������Ȩ���ʣ�*/ {
			Log.e("my-debug",path+"�����ļ�Ҳ�����ļ���,ʵ������Ȩ����");
			ResultItem item=new ResultItem();
			addResultItem(item);
		}

		// ���һ���̶߳�û�������ǲ��ᴥ��onComplete�ģ������ֶ�������������Ϊ�˱���ǰ�ļ����Ѿ���������ˣ���ʵ���ļ�������Ϊû���ļ����ļ��У���СΪ0���Ѿ����������ˣ�
		if (!isRunning()) {
			onComplete();
		}
	}

	private int mStartedThreadCount;
	private boolean mIsRunning;

	/**
	 * @return
	 */
	private boolean isComplete() {
		return mCompletedThreadCount == mStartedThreadCount;
	}

	private synchronized boolean isRunning() {
		return mIsRunning;
	}

	private synchronized void onItemComplete(AsyncThread thread) {
		mCompletedThreadCount++;

		AsyncThread r=thread;
		addSize(r.getResult());
		addResultItem(r.getResultItem());
		Log.i("my-debug", "onItemComplete:����" + mCompletedThreadCount +", ���μ�������"+r.getResult()
				+", resultItem.size:"+r.getResultItem().size+", resultItem.folderCount:"+r.getResultItem().folderCount+
				", lastFile:" + r.lastFileString);
		
		if (isComplete()) {
			onComplete();
		}
	}

	/**
	 * ȫ���߳����
	 */
	private void onComplete() {
		Log.i("my-debug", "������ onComplete: ���õ��߳���" + mStartedThreadCount + "�� ����ɵ��߳�����" + mCompletedThreadCount
				+",path:" + mPath + ", size:" + mTotalSize + ", handler:" + mHandler
				+",file����"+ mResultItem.fileCount);
		ResultItem item = mResultItem;
		//item.size = mTotalSize;
		item.path = mPath;
		mResultQueue.addFirst(item);

		if (mHandler != null) {
			Message msg = new Message();
			msg.obj = item;
			mHandler.sendMessage(msg);
		}

		//�ָ���ʼֵ
		mStartedThreadCount = 0;
		mCompletedThreadCount = 0;
		mResultItem=null;
		mIsRunning = false;// ��ʶ״̬��������һ������

		calc();// �̳���һ������
	}

	class AnalyBinder extends Binder {
		AnalyService getService() {
			return AnalyService.this;
		}

		@Override
		public IInterface queryLocalInterface(String descriptor) {
			// TODO Auto-generated method stub
			return super.queryLocalInterface(descriptor);
		}
	}

	private class AsyncThread extends Thread {
		private File[] mFiles;
		private long mSize;
		private Callback mCallback;
		public String lastFileString;
		public int fileCount;
		public int folderCount;

		private AsyncThread(File[] files, Callback finishCallback) {
			mFiles = files;
			mSize = 0;
			mCallback = finishCallback;
		}

		public void run() {
			super.run();
			for (File file : mFiles) {
				FolderHelper helper = new FolderHelper(mToken);
				long tempSize = helper.getFileSize(file);
				mIsFinished = tempSize != -1;
				mSize += tempSize;
				fileCount+=helper.getTotalFile();
				folderCount+=helper.getTotalFolder();
				lastFileString = file.getPath();
			}
			mCallback.onFinish(this);
		}

		/**
		 * �@ȡӋ��Y��
		 * 
		 * @return
		 */
		public long getResult() {
			return mSize;
		}
		
		public ResultItem getResultItem() {
			ResultItem ret=new ResultItem();
			ret.size=mSize;
			ret.fileCount=fileCount;
			ret.folderCount=folderCount;
			return ret;
		}
	}

	private static interface Callback {
		public void onFinish(Object obj);
	}

	public static class ResultItem {
		public long size;
		public String path;
		public int fileCount;
		public int folderCount;

		public String getSizeString() {
			return FolderHelper.sizeToString(size);
		}
		
		public String getfileCountString() {
			return fileCount+ "�ļ�";
		}
		
		public String getfolderCountString() {
			return folderCount+ "�ļ���";
		}
	}
}
