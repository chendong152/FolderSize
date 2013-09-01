import java.io.File;

import com.td.android.foldersize.FolderHelper;

import android.test.AndroidTestCase;
import android.util.Log;

public class FolderSizeText extends AndroidTestCase {

	@Override
	public void testAndroidTestCaseSetupProperly() {
		// TODO Auto-generated method stub
		super.testAndroidTestCaseSetupProperly();

	}

	
	public void Test() throws Exception {
		FolderHelper helper=new FolderHelper(null);
		helper.getFileSize(new File("/sdcard/Android/data/com.cooliris.media"));
		String msg="begin test case:文件："+helper.getTotalFile()+",文件夹："+helper.getTotalFolder();
		Log.i("my-debug", msg);
		System.out.println(msg);
		assertNotSame(11111111, 0);
	}
}
