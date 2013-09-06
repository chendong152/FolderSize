package com.td.android.foldersize;

import java.io.File;
import java.util.Comparator;
import java.util.Map;

import android.util.Log;

public class FileComparator implements Comparator<Map<String, Object>> {
	private int mSortType;

	public FileComparator(Object sort) {
		if (sort.getClass() == String.class)
			sort = Integer.parseInt((String) sort);
		mSortType = (Integer) sort;
	}

	private int fileTypeCompare(Map<String, Object> lhs, Map<String, Object> rhs) {
		File lFile = (File) lhs.get("file");
		File rFile = (File) rhs.get("file");
		int l = lFile.isDirectory() ? 0 : 1;
		int r = rFile.isDirectory() ? 0 : 1;
		return l - r;
	}

	@Override
	public int compare(Map<String, Object> lhs, Map<String, Object> rhs) {
		int t = fileTypeCompare(lhs, rhs);
		if (t != 0)
			return t;
		switch (mSortType) {
		case MainActivity.ORDER_BY_NAME:
			String l = (String) lhs.get("text");
			String r = (String) rhs.get("text");
			if (l == null)
				return -1;
			if (r == null)
				return 1;
			return l.toUpperCase().compareTo(r.toUpperCase());//不区分大小写

		case MainActivity.ORDER_BY_SIZE:
			Object li = lhs.get("size");
			Object ri = rhs.get("size");
			if(ri==li)
				return 0;//包括都为null
			if (ri == null)
				return 1;
			if (li == null)
				return -1;
			Log.i("my-debug", "比较：li:" + li + ",ri:" + ri + ",result:" + ((Long) ri).compareTo((Long) li));
			return -((Long) li).compareTo((Long) ri);

		default:
			break;
		}
		return 0;
	}
}