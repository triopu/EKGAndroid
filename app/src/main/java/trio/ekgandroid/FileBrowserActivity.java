package trio.ekgandroid;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileBrowserActivity extends Activity {
    //Intent Action Constants
    public static final String INTENT_ACTION_SELECT_DIR = "trio.ekgandroid.SELECT_DIRECTORY_ACTION";
    public static final String INTENT_ACTION_SELECT_FILE = "trio.ekgandroid.SELECT_FILE_ACTION";

    //Intent parameters names constants
    public static final String startDirectoryParameter = "trio.ekgandroid.directoryPath";
    public static final String returnDirectoryParameter = "trio.ekgandroid.directoryPathRet";
    public static final String returnFileParameter = "trio.ekgandroid.filePathRet";
    public static final String showCannotReadParameter = "trio.ekgandroid.showCannotRead";
    public static final String filterExtension = "trio.ekgandroid.filterExtension";

    //Stores names of traversed directories
    ArrayList<String> pathDirList = new ArrayList<String>();

    private static final String LOGTAG = "F_PATH";

    private List<Item> fileList = new ArrayList<Item>();
    private File path = null;
    private String chosenFile;

    ArrayAdapter<Item> adapter;

    private boolean showHiddenFilesAndDirs = true;

    private boolean directoryShownIsEmpty = false;

    private String filterFileExtension = null;

    //Action constants
    private static int currentAction = -1;
    private static final int SELECT_DIRECTORY = 1;
    private static final int SELECT_FILE = 2;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browsefile);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        Intent thisInt = this.getIntent();
        currentAction = SELECT_DIRECTORY;

        if (thisInt.getAction().equalsIgnoreCase(INTENT_ACTION_SELECT_FILE)){
            Log.d(LOGTAG,"SELECT ACTION - SELECT FILE");
            currentAction = SELECT_FILE;
        }

        showHiddenFilesAndDirs = thisInt.getBooleanExtra(showCannotReadParameter, true);
        filterFileExtension = thisInt.getStringExtra(filterExtension);
        setInitialDirectory();
        parseDirectoryPath();
        loadFileList();

        this.createFileListAdapter();
        this.initializeButtons();
        this.initializeFileListView();
        updateCurrentDirectoryTextView();
        Log.d(LOGTAG, path.getAbsolutePath());
    }

    private void setInitialDirectory(){
        Intent thisInt = this.getIntent();
        String requestedStartDir = thisInt.getStringExtra(startDirectoryParameter);

        if(requestedStartDir != null && requestedStartDir.length()>0){
            File tempFile = new File(requestedStartDir);
            if(tempFile.isDirectory()) this.path = tempFile;
        }

        if(this.path == null){
            if (Environment.getExternalStorageDirectory().isDirectory() && Environment.getExternalStorageDirectory().canRead())
                path = Environment.getExternalStorageDirectory();
            else
                path = new File("/");
        }
    }

    private void parseDirectoryPath(){
        pathDirList.clear();
        String pathString = path.getAbsolutePath();
        String[]parts = pathString.split("/");
        int i = 0;
        while(i < parts.length){
            pathDirList.add(parts[i]);
            i++;
        }
    }

    private void initializeButtons(){
        Button upDirButton = (Button)this.findViewById(R.id.upDirButton);
        upDirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(LOGTAG,"onclick for upDirButton");
                loadDirectoryUp();
                loadFileList();
                adapter.notifyDataSetChanged();
                updateCurrentDirectoryTextView();
            }
        });

        Button SelectFolder = (Button) this.findViewById(R.id.SelectFolder);
        if(currentAction == SELECT_DIRECTORY){
            SelectFolder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(LOGTAG, "onclick for SelectFolder Button");
                    returnDirectoryFinishActivity();
                }
            });
        }else{
            SelectFolder.setVisibility(View.GONE);
        }
    }

    private void loadDirectoryUp(){
        //Present directory removed from list
        String s = pathDirList.remove(pathDirList.size() - 1);
        //Path modified to exclde present directory
        path = new File(path.toString().substring(0,path.toString().lastIndexOf(s)));
        fileList.clear();
    }

    private void updateCurrentDirectoryTextView(){
        int i = 0;
        String curDirString = "";
        while (i < pathDirList.size()){
            curDirString += pathDirList.get(i) + "/";
            i++;
        }
        if(pathDirList.size() == 0){
            ((Button)this.findViewById(R.id.upDirButton)).setEnabled(false);
            curDirString = "/";
        }else
            ((Button)this.findViewById(R.id.upDirButton)).setEnabled(true);
        long freeSpace = getFreeSpace(curDirString);
        String formattedSpaceString = formatBytes(freeSpace);
        if(freeSpace == 0){
            Log.d(LOGTAG, "NO FREE SPACE");
            File currentDir = new File(curDirString);
            if(!currentDir.canWrite())
                formattedSpaceString = "NON Writable";
        }

        //((Button)this.findViewById(R.id.SelectFolder)).setText("Select\n[" + formattedSpaceString + "]");
        ((TextView)this.findViewById(R.id.CurrentDir)).setText("Current directory:"+curDirString);
    }

    private void showToast(String message){
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void initializeFileListView(){
        ListView lView = (ListView)this.findViewById(R.id.fileListView);
        lView.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lParam = new LinearLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT
        );
        lParam.setMargins(15,5,15,5);
        lView.setAdapter(this.adapter);
        lView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                chosenFile = fileList.get(position).file;
                File sel = new File(path + "/" + chosenFile);
                Log.d(LOGTAG, "Clicked:" + chosenFile);
                if (sel.isDirectory()) {
                    if (sel.canRead()) {
                        pathDirList.add(chosenFile);
                        path = new File(sel + "");
                        loadFileList();
                        adapter.notifyDataSetChanged();
                        updateCurrentDirectoryTextView();
                        Log.d(LOGTAG, path.getAbsolutePath());
                    } else {
                        showToast("Path does not exist or cannot be read");
                    }
                } else {
                    Log.d(LOGTAG, "item clicked");
                    if (!directoryShownIsEmpty) {
                        Log.d(LOGTAG, "File selected:" + chosenFile);
                        returnFileFinishActivity(sel.getAbsolutePath());
                    }
                }
            }
        });
    }

    public void returnDirectoryFinishActivity(){
        Intent retIntent = new Intent();
        retIntent.putExtra(returnDirectoryParameter, path.getAbsolutePath());
        this.setResult(RESULT_OK, retIntent);
        this.finish();
    }

    private void returnFileFinishActivity(String filepath){
        Intent retIntent = new Intent();
        retIntent.putExtra(returnFileParameter, filepath);
        this.setResult(RESULT_OK, retIntent);
        this.finish();
    }

    private void loadFileList(){
        try{
            path.mkdirs();
        }catch(SecurityException e){
            Log.d(LOGTAG,"unable to write on the sd card");
        }
        fileList.clear();

        if (path.exists() && path.canRead()){
            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    File sel = new File(dir, filename);
                    boolean showReadableFile = showHiddenFilesAndDirs || sel.canRead();
                    if (currentAction == SELECT_DIRECTORY){
                        return (sel.isDirectory() && showReadableFile);
                    }
                    if (currentAction == SELECT_FILE){
                        if (sel.isFile() && filterFileExtension != null){
                            return (showReadableFile && sel.getName().endsWith(filterFileExtension));
                        }
                        return (showReadableFile);
                    }
                    return true;
                }
            };

            String[] flist = path.list(filter);
            this.directoryShownIsEmpty = false;
            for(int i=0;i<flist.length;i++){
                File sel = new File(path, flist[i]);
                Log.d(LOGTAG,"File:"+flist[i]+"readable:"+(Boolean.valueOf(sel.canRead())).toString());
                int drawableID = R.drawable.folder_icon;
                boolean canRead = sel.canRead();

                //Set Drawable
                if(sel.isDirectory()){
                    if(canRead){
                        drawableID = R.drawable.folder_icon;
                    }else{
                        drawableID = R.drawable.folder_icon_light;
                    }
                }
                fileList.add(i, new Item(flist[i], drawableID, canRead));
            }
            if(fileList.size()==0){
                this.directoryShownIsEmpty = true;
                fileList.add(0, new Item("Directory is Empty", -1, true));
            }else{
                Collections.sort(fileList, new ItemFileNameComparator());
            }
        }else{
            Log.e(LOGTAG, "path does not exist or cannot be read");
        }
    }

    private void createFileListAdapter(){
        adapter = new ArrayAdapter<Item>(this, android.R.layout.select_dialog_item, android.R.id.text1, fileList){
            @Override
            public View getView(int position, View convertView, ViewGroup parent){
                //Creates View
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                //Put the image on the text view
                int drawableID = 0;
                if (fileList.get(position).icon != -1){
                    drawableID = fileList.get(position).icon;
                }
                textView.setCompoundDrawablesWithIntrinsicBounds(drawableID, 0, 0, 0);
                textView.setEllipsize(null);

                //add margin between image and text(support various screen densities)
                int dp3 = (int)(3*getResources().getDisplayMetrics().density+0.5f);
                textView.setCompoundDrawablePadding(dp3);
                textView.setBackgroundColor(Color.WHITE);
                return view;
            }
        };
    }

    public class Item{
        public String file;
        public int icon;
        public boolean canRead;

        public Item(String file, Integer icon, boolean canRead){
            this.file = file;
            this.icon = icon;
        }

        @Override
        public String toString(){
            return  file;
        }
    }

    public class ItemFileNameComparator implements Comparator<Item>{
        public int compare(Item lhs, Item rhs){
            return lhs.file.toLowerCase().compareTo(rhs.file.toLowerCase());
        }
    }

    public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE){
            Log.d(LOGTAG,"ORIENTATION_LANDSCAPE");
        }else if(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Log.d(LOGTAG,"ORIENTATION_POTRAIT");
        }
    }

    public static long getFreeSpace(String path){
        StatFs stat = new StatFs(path);
        long availSize = (long)stat.getAvailableBlocks()*(long)stat.getBlockSize();
        return availSize;
    }

    public static String formatBytes(long bytes){
        //TODO: add flag to which part is needed (e.g. GB, MB,KB or Bytes)
        String retStr = "";
        //One binary gigabyte equals 1.073.741.823 bytes
        if(bytes > 1073741824) {//Add GB
            long gbs = bytes / 1073741824;
            retStr += (new Long(gbs)).toString() + "GB";
            bytes = bytes - (gbs * 1073741824);
        }

        if(bytes > 1048576) {//Add MB
            long mbs = bytes / 1048576;
            retStr += (new Long(mbs)).toString() + "MB";
            bytes = bytes - (mbs * 1048576);
        }

        if(bytes > 1024) {//Add KB
            long kbs = bytes / 1024;
            retStr += (new Long(kbs)).toString() + "KB";
            bytes = bytes - (kbs * 1024);
        }else
            retStr += (new Long(bytes)).toString() + "byte";
        return retStr;
    }
}
