package com.td.android.foldersize;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

import com.td.android.foldersize.AnalyService.ResultItem;

import android.content.Intent;
import android.net.Uri;
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
	
	/**
	 * 递归删除文件
	 * @param file
	 */
	public static boolean recursionDeleteFile(File file){
        if(file.isFile()){
        	return file.delete();
        }
        if(file.isDirectory()){
            File[] childFile = file.listFiles();
            if(childFile == null || childFile.length == 0){
                return file.delete(); 
            }
            for(File f : childFile){
                boolean r= recursionDeleteFile(f);
                if(!r)
                	return r;//其实就是false
            }
            return file.delete();
        }
        return false;
    }

	public static boolean isImg(String path) {
		String end=path.substring(path.lastIndexOf(".") + 1,path.length()).toLowerCase();
		return end.equals("jpg")||end.equals("gif")||end.equals("png")||  
        end.equals("jpeg")||end.equals("bmp");
	}
	public static Intent openFile(File file){  
		return openFile(file.getPath());
	}
	
	public static Intent openFile(String filePath){  
		  
        File file = new File(filePath);  
        if(!file.exists()) return null;  
        /* 取得扩展名 */  
        String end=file.getName().substring(file.getName().lastIndexOf(".") + 1,file.getName().length()).toLowerCase();   
        /* 依扩展名的类型决定MimeType */  
        if(end.equals("m4a")||end.equals("mp3")||end.equals("mid")||  
                end.equals("xmf")||end.equals("ogg")||end.equals("wav")){  
            return getAudioFileIntent(filePath);  
        }else if(end.equals("3gp")||end.equals("mp4")){  
            return getAudioFileIntent(filePath);  
        }else if(isImg(end)){  
            return getImageFileIntent(filePath);  
        }else if(end.equals("apk")){  
            return getApkFileIntent(filePath);  
        }else if(end.equals("ppt")){  
            return getPptFileIntent(filePath);  
        }else if(end.equals("xls")){  
            return getExcelFileIntent(filePath);  
        }else if(end.equals("doc")){  
            return getWordFileIntent(filePath);  
        }else if(end.equals("pdf")){  
            return getPdfFileIntent(filePath);  
        }else if(end.equals("chm")){  
            return getChmFileIntent(filePath);  
        }else if(end.equals("txt")){  
            return getTextFileIntent(filePath,false);  
        }else{  
            return getAllIntent(filePath);  
        }  
    }  
      
    //Android获取一个用于打开文件的intent  
    public static Intent getAllIntent( String param ) {  
  
        Intent intent = new Intent();    
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);    
        intent.setAction(android.content.Intent.ACTION_VIEW);    
        Uri uri = Uri.fromFile(new File(param ));  
        intent.setDataAndType(uri,"*/*");   
        return intent;  
    }  
    //Android获取一个用于打开APK文件的intent  
    public static Intent getApkFileIntent( String param ) {  
  
        Intent intent = new Intent();    
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);    
        intent.setAction(android.content.Intent.ACTION_VIEW);    
        Uri uri = Uri.fromFile(new File(param ));  
        intent.setDataAndType(uri,"application/vnd.android.package-archive");   
        return intent;  
    }  
  
    //Android获取一个用于打开VIDEO文件的intent  
    public static Intent getVideoFileIntent( String param ) {  
  
        Intent intent = new Intent("android.intent.action.VIEW");  
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);  
        intent.putExtra("oneshot", 0);  
        intent.putExtra("configchange", 0);  
        Uri uri = Uri.fromFile(new File(param ));  
        intent.setDataAndType(uri, "video/*");  
        return intent;  
    }  
  
    //Android获取一个用于打开AUDIO文件的intent  
    public static Intent getAudioFileIntent( String param ){  
  
        Intent intent = new Intent("android.intent.action.VIEW");  
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);  
        intent.putExtra("oneshot", 0);  
        intent.putExtra("configchange", 0);  
        Uri uri = Uri.fromFile(new File(param ));  
        intent.setDataAndType(uri, "audio/*");  
        return intent;  
    }  
  
    //Android获取一个用于打开Html文件的intent     
    public static Intent getHtmlFileIntent( String param ){  
  
        Uri uri = Uri.parse(param ).buildUpon().encodedAuthority("com.android.htmlfileprovider").scheme("content").encodedPath(param ).build();  
        Intent intent = new Intent("android.intent.action.VIEW");  
        intent.setDataAndType(uri, "text/html");  
        return intent;  
    }  
  
    //Android获取一个用于打开图片文件的intent  
    public static Intent getImageFileIntent( String param ) {  
  
        Intent intent = new Intent("android.intent.action.VIEW");  
        intent.addCategory("android.intent.category.DEFAULT");  
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);  
        Uri uri = Uri.fromFile(new File(param ));  
        intent.setDataAndType(uri, "image/*");  
        return intent;  
    }  
  
    //Android获取一个用于打开PPT文件的intent     
    public static Intent getPptFileIntent( String param ){    
  
        Intent intent = new Intent("android.intent.action.VIEW");     
        intent.addCategory("android.intent.category.DEFAULT");     
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     
        Uri uri = Uri.fromFile(new File(param ));     
        intent.setDataAndType(uri, "application/vnd.ms-powerpoint");     
        return intent;     
    }     
  
    //Android获取一个用于打开Excel文件的intent     
    public static Intent getExcelFileIntent( String param ){    
  
        Intent intent = new Intent("android.intent.action.VIEW");     
        intent.addCategory("android.intent.category.DEFAULT");     
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     
        Uri uri = Uri.fromFile(new File(param ));     
        intent.setDataAndType(uri, "application/vnd.ms-excel");     
        return intent;     
    }     
  
    //Android获取一个用于打开Word文件的intent     
    public static Intent getWordFileIntent( String param ){    
  
        Intent intent = new Intent("android.intent.action.VIEW");     
        intent.addCategory("android.intent.category.DEFAULT");     
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     
        Uri uri = Uri.fromFile(new File(param ));     
        intent.setDataAndType(uri, "application/msword");     
        return intent;     
    }     
  
    //Android获取一个用于打开CHM文件的intent     
    public static Intent getChmFileIntent( String param ){     
  
        Intent intent = new Intent("android.intent.action.VIEW");     
        intent.addCategory("android.intent.category.DEFAULT");     
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     
        Uri uri = Uri.fromFile(new File(param ));     
        intent.setDataAndType(uri, "application/x-chm");     
        return intent;     
    }     
  
    //Android获取一个用于打开文本文件的intent     
    public static Intent getTextFileIntent( String param, boolean paramBoolean){     
  
        Intent intent = new Intent("android.intent.action.VIEW");     
        intent.addCategory("android.intent.category.DEFAULT");     
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     
        if (paramBoolean){     
            Uri uri1 = Uri.parse(param );     
            intent.setDataAndType(uri1, "text/plain");     
        }else{     
            Uri uri2 = Uri.fromFile(new File(param ));     
            intent.setDataAndType(uri2, "text/plain");     
        }     
        return intent;     
    }    
    //Android获取一个用于打开PDF文件的intent     
    public static Intent getPdfFileIntent( String param ){     
  
        Intent intent = new Intent("android.intent.action.VIEW");     
        intent.addCategory("android.intent.category.DEFAULT");     
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);     
        Uri uri = Uri.fromFile(new File(param ));     
        intent.setDataAndType(uri, "application/pdf");     
        return intent;     
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
