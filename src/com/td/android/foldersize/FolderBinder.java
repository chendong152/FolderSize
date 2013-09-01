/*
 * Author:	Dong [mailto:techdong@hotmail.com]
 * Date:	ионГ11:58:33
 */
package com.td.android.foldersize;

import android.os.Binder;
import android.os.IBinder;

/**
 * @author dong
 *
 */
public class FolderBinder extends Binder {

	public static IBinder create(String string) {
		// TODO Auto-generated method stub
		IBinder retBinder=new Binder(); 
		return retBinder;
	}
	
}
