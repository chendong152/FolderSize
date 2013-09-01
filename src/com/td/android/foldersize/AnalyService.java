/*
 * Author:	Dong [mailto:techdong@hotmail.com]
 * Date:	上午11:55:40
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
	private Handler mHandler;// 异步计算，计算完成后通过hander.sendMessage通知
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
	 * 是否计算完成。
	 * 
	 * @return 为true表示正常计算完成；为false表示被中止。
	 */
	public boolean IsFinished() {
		return mIsFinished;
	}

	/**
	 * 
	 * @param handler
	 *            参数2指定是否完成计算。
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
	private int mCompletedThreadCount = 0;// 已完成的线程数。完成指线程已停止，并非指完成最终结果的计算。

	private LinkedList<String> mPathQueue = new LinkedList<String>();// 任务队列。其中的路径为要计算的路径
	private LinkedList<ResultItem> mResultQueue = new LinkedList<ResultItem>();// 结果队列。其中的路径为已完成计算的路径。

	/*public ResultItem getResutItem() {
		return mResultQueue.poll();// 弹出队列头的路径
	}*/

	public void stop() {
		mPathQueue.clear();
		mToken.canceled = true;
	}

	/**
	 * 开始计算指定文件夹的大小
	 * 
	 * @param path
	 * @throws InterruptedException
	 */
	public void begin(String path, boolean clearOld) {
		Log.i("my-debug", "Begin path:" + path + ",  isRunning:" + isRunning()+", 上一个文件："+mPath);
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
		
		// 如果正在計算指定文件夾,繼續計算。
		if (mPath != null && mPath.equals(path)) {
			if (clearOld) {
				mPathQueue.clear();// 清空以前队列的同时
			}
			return;
		}else {
			if (clearOld) {
				stop();// 清空以前队列的同时，终止以前的计算
			}
		}

		// 指定文件夾已被添加到隊列：将其移动到头
		if (mPathQueue.contains(path)) {
			mPathQueue.remove(path);
			mPathQueue.addFirst(path);
		}

		mPathQueue.add(path);

		if (!isRunning())// 如果当前不在计算中，则开始计算。
			calc();
	}

	/**
	 * @param path
	 */
	private synchronized void calc() {
		mToken.setCanceld(false);// 记录标识为“未中止”
		if (mPathQueue.isEmpty())
			return;

		String path = mPathQueue.poll();

		Log.i("my-debug", "进入了calc,当前已启动的线程数：" + mStartedThreadCount + "， 已完成的线程数：" + mCompletedThreadCount
				+ ", 即将计算path:" + path);

		mPath = path;
		mTotalSize = 0;

		Callback finishCallback = new Callback() {
			public void onFinish(Object obj) {
				AsyncThread r = (AsyncThread) obj;
				onItemComplete(r);
			}
		};

		// 开始3条线程来执行计算,分别执行指定的子文件夹
		File file = new File(mPath);
		if (file.isDirectory()) {
			File[] allFolders = file.listFiles(new FileFilter() {
				public boolean accept(File pathname) {
					return pathname.isDirectory();
				}
			});
			if (allFolders != null && allFolders.length > 0) {
				mIsRunning = true;// 只有当启动了线程后才会进入“执行中”
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

			// 1条线程执行当前文件夹下的文件的总大小
			File[] allFiles = file.listFiles(new FileFilter() {
				public boolean accept(File pathname) {
					return pathname.isFile();
				}
			});
			if (allFiles != null && allFiles.length > 0) {
				mIsRunning = true;// 只有当启动了线程后才会进入“执行中”
				mStartedThreadCount++;
				AsyncThread thread = new AsyncThread(allFiles, finishCallback);
				thread.start();
			}
			
			//如果是空文件夹（没有任何子文件和文件夹），直接获得结果
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
		}else/*不存在（实际是无权访问）*/ {
			Log.e("my-debug",path+"不是文件也不是文件夹,实际是无权访问");
			ResultItem item=new ResultItem();
			addResultItem(item);
		}

		// 如果一条线程都没有启动是不会触发onComplete的，所以手动触发。触发是为了表达，当前文件夹已经计算完成了（其实是文件夹下因为没有文件和文件夹，大小为0，已经算计算完成了）
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
		Log.i("my-debug", "onItemComplete:次数" + mCompletedThreadCount +", 本次计算结果："+r.getResult()
				+", resultItem.size:"+r.getResultItem().size+", resultItem.folderCount:"+r.getResultItem().folderCount+
				", lastFile:" + r.lastFileString);
		
		if (isComplete()) {
			onComplete();
		}
	}

	/**
	 * 全部线程完成
	 */
	private void onComplete() {
		Log.i("my-debug", "服務中 onComplete: 启用的线程数" + mStartedThreadCount + "， 已完成的线程数：" + mCompletedThreadCount
				+",path:" + mPath + ", size:" + mTotalSize + ", handler:" + mHandler
				+",file数："+ mResultItem.fileCount);
		ResultItem item = mResultItem;
		//item.size = mTotalSize;
		item.path = mPath;
		mResultQueue.addFirst(item);

		if (mHandler != null) {
			Message msg = new Message();
			msg.obj = item;
			mHandler.sendMessage(msg);
		}

		//恢复初始值
		mStartedThreadCount = 0;
		mCompletedThreadCount = 0;
		mResultItem=null;
		mIsRunning = false;// 标识状态，进行下一个任务

		calc();// 继承下一个任务
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
		 * 獲取計算結果
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
			return fileCount+ "文件";
		}
		
		public String getfolderCountString() {
			return folderCount+ "文件夹";
		}
	}
}
