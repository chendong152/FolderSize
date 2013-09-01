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
	 *            �������Ҫ��Ӌ���^������ֹ������null
	 */
	public FolderHelper(CancelToken token) {
		mToken = token;
	}

	/**
	 * Ӌ��ָ���ļ����ļ��A��С��
	 * 
	 * @param folder
	 * @return ����-1��ʾӋ��δ��ɾͱ��Kֹ
	 */
	public long getFileSize(File folder) {
		ItemCount count = new ItemCount();
		long ret = inerGetFolderSize(folder, count);
		if (count.fileCount != totalFile || count.folderCount != totalFolder) {
			Log.e("my-debug", "�����ļ��У���ǰ·��:" + folder.getPath() + ",�ļ�����" + totalFile + " �� " + count.fileCount
					+ ",�ļ�������" + totalFolder + " �� " + count.folderCount);
		}
		return ret;
	}

	private long inerGetFolderSize(File folder, ItemCount counter) {
		Log.i("my-debug", "cache ��Ŀ��:" + mCacheSizeMap.size());
		// ����
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

		// ���뻺��
		ResultItem item = new ResultItem();
		item.size = foldersize;
		item.fileCount = counter.fileCount;
		item.folderCount = counter.folderCount;
		mCacheSizeMap.put(folder.getPath(), item);
		Log.i("my-debug", "�����ļ��У���ǰ·��:" + folder.getPath() + ",�ļ�����" + count.fileCount + ",�ļ�������" + count.folderCount);
		// System.out.println("Folder: " + folder.getName()
		// +"-------��"+sizeToString(foldersize) );
		return foldersize;
	}

	public int getTotalFolder() {
		return totalFolder;
	}

	/**
	 * �@ȡӋ���^���ļ��Ĕ������������Ӌ�����һ���ļ����򷵻�ֵӦ��Ϊ 1.
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
