package com.td.android.foldersize;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.td.android.foldersize.AnalyService.ResultItem;

import android.R.integer;
import android.os.ResultReceiver;
import android.text.StaticLayout;
import android.util.Log;

public class FolderHelper {
	int totalFolder = 0;
	int totalFile = 0;
	CancelToken mToken;

	private static ConcurrentHashMap<String, ResultItem> mCacheSizeMap = new ConcurrentHashMap<String, ResultItem>();

	/**
	 * 
	 * @param token
	 *            如果不需要在計算過程中中止，传递null
	 */
	public FolderHelper(CancelToken token) {
		mToken = token;
	}

	/**
	 * 計算指定文件或文件夾大小。
	 * 
	 * @param folder
	 * @return 返回-1表示計算未完成就被終止
	 */
	public long getFileSize(File folder) {
		ItemCount count = new ItemCount();
		long ret = inerGetFolderSize(folder, count);
		if (count.fileCount != totalFile || count.folderCount != totalFolder) {
			Log.e("my-debug", "计算文件夹：当前路径:" + folder.getPath() + ",文件数：" + totalFile + " 比 " + count.fileCount
					+ ",文件夹数：" + totalFolder + " 比 " + count.folderCount);
		}
		return ret;
	}

	private long inerGetFolderSize(File folder, ItemCount counter) {
		Log.i("my-debug", "cache 条目数:" + mCacheSizeMap.size());
		// 缓存
		if (mCacheSizeMap.containsKey(folder.getPath())) {
			Log.i("my-debug", "from cache,path:" + folder.getPath());
			ResultItem cacheItem = mCacheSizeMap.get(folder.getPath());
			totalFile = cacheItem.fileCount;
			totalFolder = cacheItem.folderCount;
			return cacheItem.size;
		}

		if (mToken != null && mToken.isCanceld()) {
			return -1;
		}

		long foldersize = 0;
		ItemCount count = new ItemCount();

		if (folder.isDirectory()) {
			totalFolder++;
			count.folderCount++;
			File[] filelist = folder.listFiles();
			for (int i = 0; filelist != null && i < filelist.length; i++) {
				if (filelist[i].isDirectory()) {
					foldersize += inerGetFolderSize(filelist[i], count);
				} else {
					totalFile++;
					count.fileCount++;
					foldersize += filelist[i].length();
				}
			}
		} else {
			totalFile++;
			count.fileCount++;
			foldersize += folder.length();
		}

		counter.fileCount += count.fileCount;
		counter.folderCount += count.folderCount;

		// 存入缓存
		ResultItem item = new ResultItem();
		item.size = foldersize;
		item.fileCount = counter.fileCount;
		item.folderCount = counter.folderCount;
		mCacheSizeMap.put(folder.getPath(), item);
		Log.i("my-debug", "计算文件夹：当前路径:" + folder.getPath() + ",文件数：" + count.fileCount + ",文件夹数：" + count.folderCount);
		// System.out.println("Folder: " + folder.getName()
		// +"-------："+sizeToString(foldersize) );
		return foldersize;
	}

	public int getTotalFolder() {
		return totalFolder;
	}

	/**
	 * 獲取計算過的文件的數量。如果本次計算的是一個文件，则返回值应该为 1.
	 * 
	 * @return
	 */
	public int getTotalFile() {
		return totalFile;
	}

	public static String sizeToString(long l) {
		String[] units = new String[] { "B", "K", "M", "G", "T" };
		final int metric = 1024;
		double ret = l;
		int i = 0;
		while (ret > metric) {
			i++;
			ret = ret / metric;
		}
		return String.format("%.2f", ret) + units[i];
	}

	public void stop() {
		if (mToken != null)
			mToken.setCanceld(true);
	}

	public static class CancelToken {
		boolean canceled;

		public boolean isCanceld() {
			return canceled;
		}

		public void setCanceld(boolean canceld) {
			this.canceled = canceld;
		}
	}

	private class ItemCount {
		public int fileCount;
		public int folderCount;
	}
}
