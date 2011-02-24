package com.happygoatstudios.bt.launcher;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.text.SpannableString;
import android.text.format.Time;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
//import android.util.Log;
import android.util.Log;
import android.util.TimeFormatException;
//import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


import com.happygoatstudios.bt.R;
import com.happygoatstudios.bt.service.IStellarService;


public class Launcher extends Activity implements ReadyListener {
	
	public static final String PREFS_NAME = "CONDIALOG_SETTINGS";
	
	private Pattern xmlinsensitive = Pattern.compile("^.+\\.[Xx][Mm][Ll]$");
	private Matcher xmlimatcher = xmlinsensitive.matcher("");

	protected static final int MESSAGE_WHATSNEW = 1;
	protected static final int MESSAGE_IMPORT = 2;
	protected static final int MESSAGE_EXPORT = 3;
	
	private ArrayList<MudConnection> connections;
	private Launcher.ConnectionAdapter apdapter;
	
	ListView lv = null;
	
	Handler actionHandler;
	
	LauncherSettings launcher_settings;
	
	IStellarService service;
	
	public enum LAUNCH_MODE {
		FREE,
		PAID,
		TEST
	}
	
	private LAUNCH_MODE mode = LAUNCH_MODE.FREE;
	private String launcher_source = "";
	
	//make this save a change
	boolean dowhatsnew = false;
	
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		//determine launch mode
		//Intent intent = this.getIntent();
		launcher_source = this.getIntent().getStringExtra("LAUNCH_MODE");
		if(launcher_source == null) {
			Log.e("BlowTorch","Launcher not provided a valid launch source. Finishing.");
			this.finish();
		}
		
		if(launcher_source.equals("com.happygoatstudios.bttest")) {
			mode = LAUNCH_MODE.TEST;
			Log.e("BlowTorch","Test Launcher Engaged.");
		} else if(launcher_source.equals("com.happygoatstudios.bt")) {
			Log.e("BlowTorch","Free Launcher Engaged.");
			mode = LAUNCH_MODE.FREE;
		} else if(launcher_source.equals("com.happygoatstudios.btpro")) {
			Log.e("BlowTorch","Paid Launcher Engaged");
			mode = LAUNCH_MODE.PAID;
		} else {
			Log.e("BlowTorch","Launcher given source: " + launcher_source + " which is invalid, Finishing");
			this.finish();
		}
		
		actionHandler = new Handler() {
			public void handleMessage(Message msg) {
				switch(msg.what) {
				case MESSAGE_WHATSNEW:
					break;
				case MESSAGE_IMPORT:

					//if the file exists, we will get here, if not, it will go to file not found.
					try {
						LauncherSAXParser parser = new LauncherSAXParser((String)msg.obj,Launcher.this);
						launcher_settings = parser.load();
					} catch (RuntimeException e) {
						AlertDialog.Builder error = new AlertDialog.Builder(Launcher.this);
						error.setTitle("Error loading XML");
						error.setMessage(e.getMessage());
						error.setPositiveButton("Acknowledge.",new DialogInterface.OnClickListener() {
							
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
							}
						});
						AlertDialog errordialog = error.create();
						errordialog.show();
						return;
					}
					//update this list to the new version.
					PackageManager m = Launcher.this.getPackageManager();
					String versionString = null;
					try {
						versionString = m.getPackageInfo("com.happygoatstudios.bt", PackageManager.GET_CONFIGURATIONS).versionName;
					} catch (NameNotFoundException e) {
						//can't execute on our package aye?
						throw new RuntimeException(e);
					}
					launcher_settings.setCurrentVersion(versionString);
					buildList();
					saveXML();
					break;
				case MESSAGE_EXPORT:
					DoExport((String)msg.obj);
					break;
				default:
					break;
				}
			}
		};
		
		setContentView(R.layout.new_launcher_layout);
		int testversion = 0;
		if(mode == LAUNCH_MODE.TEST) {
			findViewById(R.id.test_update).setVisibility(View.VISIBLE);
			try {
				//this.getPackageManager().getPackageInfo(launcher_source, PackageManager.GET_META_DATA).;
				ApplicationInfo testLauncher = this.getPackageManager().getApplicationInfo(launcher_source, PackageManager.GET_META_DATA);
				if(testLauncher != null) {
					if(testLauncher.metaData != null) {
						testversion = testLauncher.metaData.getInt("BLOWTORCH_TEST_VERSION");
					} else {
						Log.e("BlowTorch","metaData is null");
						return;
					}
				
				} else {
					Log.e("BlowTorch","ApplicationInfo is null");
					return;
				}
				//int testversion = this.getPackageManager().getApplicationInfo(launcher_source, PackageManager.GET_META_DATA).metaData.getInt("TEST_VERSION");
				((TextView)findViewById(R.id.update_label)).setText("Test Version " + testversion);
				
				boolean needsupdate = true;
				
				
				
				if(needsupdate) {
					//check if file exists.
					findViewById(R.id.update_button).setVisibility(View.VISIBLE);
					findViewById(R.id.update_button).setOnClickListener(new View.OnClickListener() {
						
						public void onClick(View v) {
							// TODO Auto-generated method stub
							updateDialog = ProgressDialog.show(Launcher.this, "", "Checking update status",true,true,new DialogInterface.OnCancelListener() {
								
								//@Override
								public void onCancel(DialogInterface dialog) {
									return;
								}
							});
							URL url2 = null;
							try {
								url2 = new URL("http://bt.happygoatstudios.com/test/version");
							} catch (MalformedURLException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							try {
								BufferedReader in = new BufferedReader(new InputStreamReader(url2.openStream()));
								StringBuffer buf = new StringBuffer();
								String tmp;
								while((tmp = in.readLine()) != null) {
									buf.append(tmp);
								}
								try {
									Integer newVersion = Integer.parseInt(buf.toString());
									Log.e("BlowTorch","Web update version: " + newVersion);
									ApplicationInfo testLauncher = Launcher.this.getPackageManager().getApplicationInfo(launcher_source, PackageManager.GET_META_DATA);
									int testversion = testLauncher.metaData.getInt("BLOWTORCH_TEST_VERSION");
									if(newVersion > testversion) {
										//needsupdate = true;
									} else {
										Toast t = Toast.makeText(Launcher.this, "BlowTorch Test Version "+testversion+" is up to date.", Toast.LENGTH_SHORT);
										t.show();
										updateDialog.dismiss();
										updateDialog = null;
										return;
									}
								} catch(NumberFormatException e) {
									Log.e("BlowTorch","Web update response: " + buf.toString());
								} catch (NameNotFoundException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							updateDialog.dismiss();
							updateDialog = null;
							
							String state = Environment.getExternalStorageState();
							if(!state.equals(Environment.MEDIA_MOUNTED)) {
								//no sd card
							} else {
								//proceed
								//attempt to download new version.
								updateDialog = ProgressDialog.show(Launcher.this, "", "Downloading update.",true,true,new DialogInterface.OnCancelListener() {
									
									//@Override
									public void onCancel(DialogInterface dialog) {
										//update.doCancel();
									}
								});
								//updateDialog.show();
								//v.invalidate();
								//updateDialog = ProgressDialog.show(Launcher.this, "Downloading", "Getting update from web.");
								//updateDialog.show();
								//Launcher.this.findViewById(R.id.)
								/*synchronized(updateHandler) {
									try {
										updateHandler.wait(3000);
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
								updateHandler.sendEmptyMessage(MESSAGE_STARTDOWNLOAD);*/
								updateHandler.sendEmptyMessageDelayed(MESSAGE_STARTDOWNLOAD,1000);
								/*String filename = Environment.getExternalStorageDirectory().getAbsolutePath() + "/BlowTorch/launcher/TestPackage.apk";
								File file = new File(filename);
								if(!file.exists()) {
									Log.e("BlowTorch","Test application update does not exist.");
									return; //file doesn't exist
								}
								
								Intent i = new Intent();
								i.setAction(Intent.ACTION_VIEW);
								Uri data = Uri.parse("file://" + filename);
								i.setDataAndType(data, "application/vnd.android.package-archive");
								startActivity(i);*/
							}
						}
					});
					
					
					
				}
				
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} //catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			//}
		}
		
		launcher_settings = new LauncherSettings();
		connections = new ArrayList<MudConnection>();
		
		lv = (ListView)findViewById(R.id.connection_list);
		apdapter = new ConnectionAdapter(lv.getContext(),R.layout.connection_row,connections);
		
		
		lv.setAdapter(apdapter);
		
		lv.setTextFilterEnabled(true);
		lv.setOnItemClickListener(new listItemClicked());
		lv.setOnItemLongClickListener(new listItemLongClicked());
		
		
		lv.setEmptyView(findViewById(R.id.launcher_empty));
		
		try { 
			FileInputStream fos = this.openFileInput("blowtorch_launcher_list.xml");
			fos.close();
			LauncherSAXParser parser = new LauncherSAXParser("blowtorch_launcher_list.xml",this);
			launcher_settings = parser.load();
			
			buildList();
			//Log.e("LAUNCHER","LOADING XML LAUNCHER");
		} catch (FileNotFoundException e) {
			//attempt to read the connections from disk.
			//Log.e("LAUNCHER","LOADING CRAPPY LAUNCHER");
			getConnectionsFromDisk();
			//fill the new settings
			int size = apdapter.getCount();
			Time t = new Time();
			t.set(System.currentTimeMillis());
			long starttime = System.currentTimeMillis();
			for(int i=0;i<size;i++) {
				MudConnection tmp = apdapter.getItem(i);
				Time oldertime = new Time();
				oldertime.set(starttime - 1000*i);
				tmp.setLastPlayed(oldertime.format2445());
				launcher_settings.getList().put(tmp.getDisplayName(), tmp.copy());
				
			}
			
			//get the version information.
			//PackageManager m = this.getPackageManager();
			//String versionString = null;
			//try {
			//	versionString = m.getPackageInfo("com.happygoatstudios.bt", PackageManager.GET_CONFIGURATIONS).versionName;
			//} catch (NameNotFoundException e1) {
				//can't execute on our package aye?
			//	throw new RuntimeException(e);
			//}
			
			//Log.e("LAUNCHER","LOADING OLD SETTINGS AND MARKING VERSION: " + versionString);
			launcher_settings.setCurrentVersion("1.0.4");
			
			saveXML();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		//by here we should have a completly populated list and settings
		//check version code.
		PackageManager m = this.getPackageManager();
		String versionString = null;
		try {
			versionString = m.getPackageInfo(launcher_source, PackageManager.GET_CONFIGURATIONS).versionName;
		} catch (NameNotFoundException e) {
			//can't execute on our package aye?
			throw new RuntimeException(e);
		}
		
		int now_major = 1;
		int now_minor = 0;
		int now_rev = 0;
		
		int prev_major = 1;
		int prev_minor = 0;
		int prev_rev = 0;
		//compare version codes.
		Pattern version = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
		Matcher vmatch = version.matcher(versionString);
		if(vmatch.matches()) {
			now_major = Integer.parseInt(vmatch.group(1));
			now_minor = Integer.parseInt(vmatch.group(2));
			now_rev = Integer.parseInt(vmatch.group(3));
		} else {
			//shouldn't really happen.
		}
		
		vmatch.reset(launcher_settings.getCurrentVersion());
		if(vmatch.matches()) {
			prev_major = Integer.parseInt(vmatch.group(1));
			prev_minor = Integer.parseInt(vmatch.group(2));
			prev_rev = Integer.parseInt(vmatch.group(3));
		} else {
			//shouldn't really happen, unless xml modification went haywire
		}
		
		boolean isoutdated = false;
		
		if(now_major > prev_major) {
			//Log.e("LAUNCHER","MAJOR NOW:" + now_major + " MAJOR PREV:" + prev_major);
			isoutdated = true;
		} else if (now_minor > prev_minor) {
			//Log.e("LAUNCHER","MINOR NOW:" + now_minor + " MINOR PREV:" + prev_minor);
			isoutdated = true;
		} else if (now_rev > prev_rev) {
			//Log.e("LAUNCHER","REV NOW:" + now_rev + " REV PREV:" + prev_rev);
			isoutdated = true;
		}
		
		if(isoutdated) {
			dowhatsnew = true;
			launcher_settings.setCurrentVersion(versionString);
			saveXML();
			//Log.e("LAUNCHER","DOING OUTATED, WAS " + launcher_settings.getCurrentVersion() + " NOW " + versionString);
		} else {
			//Log.e("LAUNCHER","NOT OUTDATED, WAS " + launcher_settings.getCurrentVersion() + " NOW " + versionString);
		}
		
		
		//getConnectionsFromDisk();
		
		Button newbutton = (Button)findViewById(R.id.new_connection);
		newbutton.setOnClickListener(new newClickedListener());
		
		Button helpbutton = (Button)findViewById(R.id.help_button);
		helpbutton.setOnClickListener(new helpClickedListener());
		
		Button donatebutton = (Button)findViewById(R.id.donate_button);
		donatebutton.setOnClickListener(new helpClickedListener());

		
	}
	
	public void onStart() {
		super.onStart();
		//if(noConnections) {
		//	Toast msg = Toast.makeText(this, "No connections specified, select NEW to create.", Toast.LENGTH_LONG);
		//	msg.show();
		//}
		if(dowhatsnew) {
			dowhatsnew = false;
			DoWhatsNew();
		}
	}
	
	public void onDestroy() {
		//saveConnectionsToDisk();
		saveXML();
		super.onDestroy();
	}
	
	private class helpClickedListener implements View.OnClickListener {

		public void onClick(View v) {
			Intent web_help = new Intent(Intent.ACTION_VIEW,Uri.parse("http://bt.happygoatstudios.com/"));
			startActivity(web_help);
		}
		
	}
	
	private class newClickedListener implements View.OnClickListener {
		public void onClick(View v) {
			//close the dialog for now
			//ConnectionPickerDialog.this.dismiss();
			NewConnectionDialog diag = new NewConnectionDialog(Launcher.this,Launcher.this);
			diag.show();
		}
	}
	
	private class listItemLongClicked implements ListView.OnItemLongClickListener {

		public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
				int arg2, long arg3) {
			//Log.e("LAUNCHER","List item long clicked!");
			MudConnection muc = apdapter.getItem(arg2);
			
			
			Message delmsg = connectionModifier.obtainMessage(MSG_DELETECONNECTION);
			delmsg.obj = muc;
			
			Message modmsg = connectionModifier.obtainMessage(MSG_MODIFYCONNECTION);
			modmsg.obj = muc;
			
			AlertDialog.Builder build = new AlertDialog.Builder(Launcher.this)
				.setMessage("Which operation to perform on: " + muc.getDisplayName());
			AlertDialog dialog = build.create();
			dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Edit", modmsg);
			dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Delete",delmsg);
			dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
				
				public void onClick(DialogInterface arg0, int arg1) {
					arg0.dismiss();
				}
			});
			
			dialog.show();
			return true;
		}
		
	}
	boolean debug = true;
	private class listItemClicked implements ListView.OnItemClickListener {
		
		//@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			
			Rect rect = new Rect();
		    Window win = Launcher.this.getWindow();
		    win.getDecorView().getWindowVisibleDisplayFrame(rect);
		    int statusBarHeight = rect.top;
		    int contentViewTop = win.findViewById(Window.ID_ANDROID_CONTENT).getTop();
		    int titleBarHeight = statusBarHeight - contentViewTop ;
		    //Log.d("ID-ANDROID-CONTENT", "titleBarHeight = " + titleBarHeight );
		    
		    SharedPreferences pref = Launcher.this.getSharedPreferences("STATUS_BAR_HEIGHT", 0);
			Editor e = pref.edit();
			e.putInt("STATUS_BAR_HEIGHT", titleBarHeight);
		    e.commit();
			
			MudConnection muc = apdapter.getItem(arg2);		
			
			Time the_time = new Time();
			the_time.set(System.currentTimeMillis());
			muc.setLastPlayed(the_time.format2445());
			
			saveXML();
			
			buildList();
			
			//if(debug) return;
			
			//Intent the_intent = new Intent(com.happygoatstudios.bt.window.MainWindow.class.getName());
	    	
	    	//the_intent.putExtra("DISPLAY",muc.getDisplayName());
	    	//the_intent.putExtra("HOST", muc.getHostName());
	    	//the_intent.putExtra("PORT", muc.getPortString());
	    	
	    	//write out the intent to the service so it can do some lookup work in advance of the connection, such as loading the settings wad
	    	//SharedPreferences prefs = Launcher.this.getSharedPreferences("SERVICE_INFO",0);
	    	//Editor edit = prefs.edit();
	    	//Log.e("WINDOW","SETTING " + muc.getDisplayName());
	    	
	    	
	    	//edit.putString("SETTINGS_PATH", muc.getDisplayName());
	    	//edit.commit();
	    	
	    	//check to see if the service is actually running
	    	
	    	boolean found = isServiceRunning();
	    	
	    	if(!found) {
    			//service is not running, reset the values in the shared prefs that the window uses to keep track of weather or not to finish init routines.
    			//kill all whitespace in the display name.
	    		launch = muc.copy();
	    		DoNewStartup();
	    	} else {
	    		//service exists, we should figure out the name of what it is playing.
	    		//Log.e("LAUNCHER","SERVICE IS RUNNING");
	    		launch = muc.copy();
	    		if(mode == LAUNCH_MODE.FREE) {
	    			bindService(new Intent(com.happygoatstudios.bt.service.IStellarService.class.getName()+".MODE_NORMAL"), mConnection, 0); //do not auto create
	    		} else {
	    			bindService(new Intent(com.happygoatstudios.bt.service.IStellarService.class.getName()+".MODE_TEST"), mConnection, 0);
	    		}
	    	}
	    	
		}

		
	}
	private MudConnection launch;
	
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName arg0, IBinder arg1) {
			
			service = IStellarService.Stub.asInterface(arg1); //turn the binder into something useful
			
			String test = "";
			String against = launch.getHostName() +":"+ launch.getPortString();
			try {
				test = service.getConnectedTo();
			} catch (RemoteException e) {
				throw new RuntimeException(e);
			}
			
			if(!test.equals(against)) {
				//does not equal, show the warning.
				AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
				builder.setMessage("Service already connected to " + test + "\nDisconnect and launch " + launch.getDisplayName() + "?");
				builder.setTitle("Currently Connected");
				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						Launcher.this.unbindService(mConnection);
						stopService(new Intent(com.happygoatstudios.bt.service.IStellarService.class.getName()));
						DoNewStartup();
					}
				});
				
				builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						Launcher.this.unbindService(mConnection);
						dialog.dismiss();
					}
				});
				AlertDialog connected = builder.create();
				connected.show();
			} else {
				//are equal, proceed with normal startup.
				Launcher.this.unbindService(mConnection);
				DoFinalStartup();
			}
			
		}

	

		public void onServiceDisconnected(ComponentName arg0) {
			
		}
		
	};
	
	
	
	public final int MSG_DELETECONNECTION = 101;
	public final int MSG_MODIFYCONNECTION = 102;
	public Handler connectionModifier = new Handler() {
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case MSG_DELETECONNECTION:
				MudConnection todelete = (MudConnection)msg.obj;
				launcher_settings.getList().remove(todelete.getDisplayName());
				buildList();
				//MudConnection todelete = (MudConnection)msg.obj;
				//apdapter.remove(todelete);
				//apdapter.notifyDataSetChanged();
				break;
			case MSG_MODIFYCONNECTION:
				MudConnection tomodify = (MudConnection)msg.obj;
				NewConnectionDialog diag = new NewConnectionDialog(Launcher.this,Launcher.this,tomodify);
				diag.show();
				break;
			default:
				break;
			}
		}
	};
	
	public void ready(MudConnection newData) {
		//promote this one to the head of the class.
		Time t = new Time();
		t.set(System.currentTimeMillis());
		newData.setLastPlayed(t.format2445());
		launcher_settings.getList().put(newData.getDisplayName(), newData);
		buildList();
		saveXML();
	}

    /*public void ready(String displayname,String host,String port) {
    	
    	
		MudConnection muc = new MudConnection();
		muc.setDisplayName(displayname);
		muc.setHostName(host);
		muc.setPortString(port);
		
		launcher_settings.getList().put(muc.getDisplayName(), muc.copy());
		buildList();


    	
    }*/
    
    public void modify(MudConnection old, MudConnection newData) {
    	launcher_settings.getList().remove(old.getDisplayName());
    	launcher_settings.getList().put(newData.getDisplayName(), newData);
    	buildList();
    	saveXML();
    }
    
	/*public void modify(String displayname, String host, String port,MudConnection old) {

		MudConnection muc = new MudConnection();
		muc.setDisplayName(displayname);
		muc.setHostName(host);
		muc.setPortString(port);
		
		apdapter.remove(old);
		
		apdapter.add(muc);
		apdapter.notifyDataSetChanged();
	}*/
    
	private void getConnectionsFromDisk() {
		//This is here for posterity. It will only be used to fallback.
		
		SharedPreferences pref = this.getSharedPreferences(PREFS_NAME, 0);
		
		String thestring = pref.getString("STRINGS", "");
		if(thestring == null || thestring == "") { return; }
		
		Pattern connection = Pattern.compile("([^\\|]+)");
		Pattern breakout = Pattern.compile("(.+):(.+):(.+)");
		
		Matcher c_m = connection.matcher(thestring);
		
		while(c_m.find()) {
			String operate = c_m.group(1);
			Matcher o_m = breakout.matcher(operate);
			while(o_m.find()) {
				String displayname = o_m.group(1);
				String hostname = o_m.group(2);
				String portstring = o_m.group(3);
				
				MudConnection muc = new MudConnection();
				muc.setDisplayName(displayname);
				muc.setHostName(hostname);
				muc.setPortString(portstring);
				
				apdapter.add(muc);
			}
		}
		
	}
	
	private void DoImportMenu() {
		
		File tmp = Environment.getExternalStorageDirectory();
		
		File btermdir = new File(tmp,"/BlowTorch/launcher/");
		
		String sdstate = Environment.getExternalStorageState();
		HashMap<String,String> xmlfiles = new HashMap<String,String>();
		if(Environment.MEDIA_MOUNTED.equals(sdstate) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(sdstate)) {
			btermdir.mkdirs();
			
			for(File xml : btermdir.listFiles(xml_only)) {
				xmlfiles.put(xml.getName(), xml.getPath());
			}
			
			final String[] entries = new String[xmlfiles.keySet().size()];
			String[] names = new String[xmlfiles.keySet().size()];
			
			if(xmlfiles.size() == 0) {
				Toast t = Toast.makeText(this, "No XML files in /BlowTorch/launcher/", Toast.LENGTH_LONG);
				t.show();
				return;
			}
			
			int i=0;
			for(String name : xmlfiles.keySet()) {
				names[i] = name;
				entries[i] = xmlfiles.get(name);
				i++;
			}
			
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Select List");
			builder.setSingleChoiceItems(names, -1,new DialogInterface.OnClickListener() {
				
				public void onClick(DialogInterface dialog, int which) {
					actionHandler.sendMessage(actionHandler.obtainMessage(MESSAGE_IMPORT, entries[which]));
					dialog.dismiss();
				}
			});
			
			//builder.setI
			
			AlertDialog dialog = builder.create();
			dialog.show();
			
		} else {
			Toast t = Toast.makeText(this, "SD card not available.", Toast.LENGTH_LONG);
			t.show();
		}
		
		
	}
	
	FilenameFilter xml_only = new FilenameFilter() {

		public boolean accept(File arg0, String arg1) {
			//return arg1.endsWith(".xml");
			xmlimatcher.reset(arg1);
			if(xmlimatcher.matches()) {
				return true;
			} else {
				return false;
			}
		}
		
	};
	
	private boolean isServiceRunning() {
		ActivityManager activityManager = (ActivityManager)Launcher.this.getSystemService(Context.ACTIVITY_SERVICE);
    	List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
    	boolean found = false;
    	for(RunningServiceInfo service : services) {
    		//Log.e("LAUNCHER","FOUND:" + service.service.getClassName());
    		//service.service.
    		if(com.happygoatstudios.bt.service.StellarService.class.getName().equals(service.service.getClassName())) {
    			//service is running, don't do anything.
    			Log.e(":Launcher","Service lives in: " + service.process);
    			if(mode == LAUNCH_MODE.FREE) {
    				
    				if(service.process.equals("com.happygoatstudios.bt:stellar_free")) found = true;
    			} else if(mode == LAUNCH_MODE.TEST) {
    				if(service.process.equals("com.happygoatstudios.bt:stellar_test")) found = true;
    			}
    			
    		} else {

    			
    		}
    	}
		return found;
	}
	
	private void DoExport(String filename) {
		
		String dir = "/BlowTorch";
		String launcher = "/launcher";
		String path = launcher + dir + filename;
		
		try {
			//tmp = BaardTERMService.this.openFileOutput(path, Context.MODE_WORLD_READABLE|Context.MODE_WORLD_WRITEABLE);
			//BaardTERMService.this.openF
			File root = Environment.getExternalStorageDirectory();
			String state = Environment.getExternalStorageState();
			if(Environment.MEDIA_MOUNTED.equals(state) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				boolean added = false;
				String updated = path;
				Pattern xmlend = Pattern.compile("^.+\\.[Xx][Mm][Ll]$");
				Matcher xmlmatch = xmlend.matcher(updated);
				String updatedname = filename;
				if(!xmlmatch.matches()) {
					added = true;
					updated = path + ".xml";
					updatedname = filename + ".xml";
				}
				
				File blowtorchdir = new File(root,dir);
				blowtorchdir.mkdirs();
				
				
				File launcherdir = new File(blowtorchdir,launcher);
				launcherdir.mkdirs();
				
				File file = new File(launcherdir,updatedname);
				
				file.createNewFile();
				
				FileWriter writer = new FileWriter(file);
				BufferedWriter tmp = new BufferedWriter(writer);
				tmp.write(LauncherSettings.writeXml(launcher_settings));
				tmp.close();
				
				String message = "Saved: " + file.getPath();
				if(added) {
					message += "\nAppended .xml extension.";
				}
				
				Toast msg = Toast.makeText(this,message,Toast.LENGTH_LONG);
				//Toast msg = Toast.makeText(StellarService.this.getApplicationContext(), message, Toast.LENGTH_SHORT);
				msg.show();
			} else {
				//Log.e("SERVICE","COULD NOT WRITE SETTINGS FILE!");
				//Toast msg = Toast.makeText(StellarService.this.getApplicationContext(), "SD Card not available. File not written.", Toast.LENGTH_SHORT);
				//msg.show();
				Toast msg = Toast.makeText(this,"SD Card not available. File not written.",Toast.LENGTH_LONG);
				msg.show();
			}
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
	}
	
	private void DoWhatsNew() { 
		
		//get the version information.
		PackageManager m = this.getPackageManager();
		String versionString = null;
		try {
			versionString = m.getPackageInfo(launcher_source, PackageManager.GET_CONFIGURATIONS).versionName;
		} catch (NameNotFoundException e) {
			//can't execute on our package aye?
			throw new RuntimeException(e);
		}
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Version " + versionString + " details!");
		
		final SpannableString s = new SpannableString(Launcher.this.getResources().getString(R.string.whatisnew));
	    Linkify.addLinks(s, Linkify.ALL);

		builder.setMessage(s);
		
		builder.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
			
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		
		AlertDialog diag = builder.create();
		
		//TextView message = (TextView) diag.findViewById(android.R.id.message);
		
		diag.show();
		
		((TextView)diag.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());

		
	}
	
	private void DoNewStartup() {
		Pattern invalidchars = Pattern.compile("\\W"); 
		Matcher replacebadchars = invalidchars.matcher(launch.getDisplayName());
		String prefsname = replacebadchars.replaceAll("") + ".PREFS";
		//prefsname = prefsname.replaceAll("/", "");
		
		SharedPreferences sprefs = Launcher.this.getSharedPreferences(prefsname,0);
		//servicestarted = prefs.getBoolean("CONNECTED", false);
		//finishStart = prefs.getBoolean("FINISHSTART", true);
		SharedPreferences.Editor editor = sprefs.edit();
		editor.putBoolean("CONNECTED", false);
		editor.putBoolean("FINISHSTART", true);
		editor.commit();
		//Log.e("LAUNCHER","SERVICE NOT STARTED, AM RESETTING THE INITIALIZER BOOLS IN " + prefsname);
		
		//Launcher.this.startActivity(the_intent);
		//SharedPreferences sprefs = Launcher.this.getSharedPreferences(prefsname,0);
		//SharedPreferences.Editor editor = sprefs.edit();
		//editor.putBoolean("CONNECTED", false);
		//editor.putBoolean("FINISHSTART", true);
		editor.commit();
		
		
		//launch = muc;
		DoFinalStartup();
	}
	
	private void DoFinalStartup() {
		Intent the_intent = null;
		if(mode == LAUNCH_MODE.TEST) {
			the_intent = new Intent("com.happygoatstudios.bt.window.MainWindow.TEST_MODE");
		} else {
			Log.e("BlowTorch","LAUNCHING NORMAL MODE!");
			the_intent = new Intent("com.happygoatstudios.bt.window.MainWindow.NORMAL_MODE");
		}
    	the_intent.putExtra("DISPLAY",launch.getDisplayName());
    	the_intent.putExtra("HOST", launch.getHostName());
    	the_intent.putExtra("PORT", launch.getPortString());
    	
    	//write out the intent to the service so it can do some lookup work in advance of the connection, such as loading the settings wad
    	//SharedPreferences prefs = Launcher.this.getSharedPreferences("SERVICE_INFO",0);
    	//Editor edit = prefs.edit();
    	//Log.e("WINDOW","SETTING " + muc.getDisplayName());
    	
    	
    	//edit.putString("SETTINGS_PATH", launch.getDisplayName());
    	//edit.commit();
		//Pattern invalidchars = Pattern.compile("\\W"); 
		//Matcher replacebadchars = invalidchars.matcher(launch.getDisplayName());
		//String prefsname = replacebadchars.replaceAll("") + ".PREFS";
		///prefsname = prefsname.replaceAll("/", "");
		
		
		//Log.e("LAUNCHER","SERVICE NOT STARTED, AM RESETTING THE INITIALIZER BOOLS IN " + prefsname);
		
    	
    	SharedPreferences prefs = Launcher.this.getSharedPreferences("SERVICE_INFO",0);
    	Editor edit = prefs.edit();
    	
    	
    	edit.putString("SETTINGS_PATH", launch.getDisplayName());
    	edit.commit();
    	
		Launcher.this.startActivity(the_intent);
	}
	
	private ConnectionComparator ccmp = new ConnectionComparator();
	
	private void buildList() {
		apdapter.clear();
		for(MudConnection m : launcher_settings.getList().values()) {
			apdapter.add(m);
		}
		
		apdapter.sort(ccmp);
		
		apdapter.notifyDataSetChanged();
	}
	
	private void saveXML() {
		try {
			FileOutputStream fos = this.openFileOutput("blowtorch_launcher_list.xml",Context.MODE_PRIVATE);
			fos.write(LauncherSettings.writeXml(launcher_settings).getBytes("UTF-8"));
			fos.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private class ConnectionComparator implements Comparator<MudConnection> {

		public int compare(MudConnection a, MudConnection b) {
			//pos it above, negative if below
			Time at = new Time();
			Time bt = new Time();
			
			//check if either have haver been played.
			if(a.getLastPlayed().equals("never")) {
				return 1;
			} else if(b.getLastPlayed().equals("never")) {
				return -1;
			} else if(b.getLastPlayed().equals("never") && a.getLastPlayed().equals("never")){
				return 0; //they are both never, so they are equal.
			}
			
			try{
				
				at.parse(a.getLastPlayed());
				bt.parse(b.getLastPlayed());
			} catch (TimeFormatException e) {
				return 0;
			}
			return Time.compare(bt, at);
		}
		
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {
		
		menu.add(0,99,0,"What's New");
		menu.add(0,100,0,"Import List");
		menu.add(0,105,0,"Export List");
		
		return true;
		
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case 99:
			//dowhatsnew
			DoWhatsNew();
			break;
		case 100:
			//start import
			DoImportMenu();
			break;
		case 105:
			//start export
            LayoutInflater factory = LayoutInflater.from(this);
            final View textEntryView = factory.inflate(R.layout.dialog_text_entry, null);
            final EditText entry = (EditText) textEntryView.findViewById(R.id.launcher_export);
        
            AlertDialog exporter = new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle("Export List")
                .setView(textEntryView)
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        actionHandler.sendMessage(actionHandler.obtainMessage(MESSAGE_EXPORT,entry.getText().toString()));
                    }
                })
                
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        dialog.dismiss();
                    }
                })
                .create();
            
            exporter.show();

			break;
		default:
			break;
		}
		
		return true;
	}
	
    
    
	private class ConnectionAdapter extends ArrayAdapter<MudConnection> {
		private ArrayList<MudConnection> items;
		
		public ConnectionAdapter(Context context, int txtviewresid, ArrayList<MudConnection> objects) {
			super(context, txtviewresid, objects);
			
			this.items = objects;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if(v == null) {
				LayoutInflater li = (LayoutInflater)this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = li.inflate(R.layout.connection_row, null);
			}
			
			MudConnection m = items.get(position);
			if(m != null) {
				TextView title = (TextView)v.findViewById(R.id.displayname);
				TextView host = (TextView)v.findViewById(R.id.hoststring);
				//TextView port = (TextView)v.findViewById(R.id.port);
				if(title != null) {
					title.setText(" " + m.getDisplayName());
				}
				if(host != null) {
					host.setText("\t"  + m.getHostName() + ":" + m.getPortString());
				}
				//if(port != null) {
				//	port.setText(" Port: " + m.getPortString());
				//}
			}
			return v;
			
			
		}
		
		
	}
	
	private final int MESSAGE_STARTUPDATE = 10098;
	private final int MESSAGE_STARTDOWNLOAD = 10099;
	private final int MESSAGE_CANCELDOWNLOAD = 10100;
	private final int MESSAGE_FINISHUPDATE = 10101;
	private final int MESSAGE_DOWNLOADEDBYTES = 10102;
	ProgressDialog updateDialog = null;
	UpdateThread update = null;
	Handler updateHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case MESSAGE_DOWNLOADEDBYTES:
				//updateDialog.incrementProgressBy(msg.arg1);
				break;
			case MESSAGE_STARTUPDATE:
				
				break;
			case MESSAGE_STARTDOWNLOAD:
				
				/*updateDialog = new ProgressDialog(Launcher.this);
				updateDialog.setCancelable(true);
				updateDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				updateDialog.setCancelMessage(this.obtainMessage(MESSAGE_CANCELDOWNLOAD));
				//updateDialog.setMax(size);
				//updateDialog.setProgress(0);
				updateDialog.show();
				*/
				
				
				synchronized(this) {
				try {
					
					this.wait(50);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				}
				/*URL updateSize;
				try {
					updateSize = new URL("http://bt.happygoatstudios.com/test/size");
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
				BufferedReader in;
				try {
					in = new BufferedReader(new InputStreamReader(updateSize.openStream()));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
				String tmp = "";
				StringBuffer buf = new StringBuffer();
				try {
					while((tmp = in.readLine())!=null) {
						buf.append(tmp);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
				Integer size = Integer.parseInt(buf.toString());
				Log.e("BlowTorch","Update size is: " + size + " bytes.");*/
				String filename = Environment.getExternalStorageDirectory().getAbsolutePath() + "/BlowTorch/launcher/TestPackage.apk";
				//proceed with download.
				File deleter = new File(filename);
				if(deleter.exists()) deleter.delete();
				
				
				
				update = new UpdateThread(this);
				update.run();
				
				
				break;
			case MESSAGE_CANCELDOWNLOAD:
				update.doCancel();
				updateDialog.dismiss();
				updateDialog = null;
				String delyou = Environment.getExternalStorageDirectory().getAbsolutePath() + "/BlowTorch/launcher/TestPackage.apk";
				//proceed with download.
				File delme = new File(delyou);
				if(delme.exists()) delme.delete();
				break;
			case MESSAGE_FINISHUPDATE:
				updateDialog.dismiss();
				updateDialog = null;
				update = null;
				String updatepath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/BlowTorch/launcher/TestPackage.apk";
				File file = new File(updatepath);
				if(!file.exists()) {
					Log.e("BlowTorch","Test application update does not exist.");
					return; //file doesn't exist
				}
				
				Intent i = new Intent();
				i.setAction(Intent.ACTION_VIEW);
				Uri data = Uri.parse("file://" + updatepath);
				i.setDataAndType(data, "application/vnd.android.package-archive");
				startActivity(i);
				Launcher.this.finish();
				break;
			}
		}
	};

	private class UpdateThread extends Thread {
		
		private boolean cancelled = false;
		private Handler reportTo = null;
		
		public UpdateThread(Handler useMe) {
			reportTo = useMe;
		}
		
		public void doCancel() {
			cancelled = true;
		}
		
		public void run() {
			//this is really just downloading the file.
			URL url = null;
			try {
				url = new URL("http://bt.happygoatstudios.com/test/TestPackage.apk");
				
				HttpURLConnection c = (HttpURLConnection) url.openConnection();
				
				c.setRequestMethod("GET");
				c.setDoOutput(true);
				c.connect();
				String updatepath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/BlowTorch/launcher/TestPackage.apk";
				File file = new File(updatepath);
				FileOutputStream fos = new FileOutputStream(file);
				
				InputStream is = c.getInputStream();
				
				byte[] buffer = new byte[8192];
				int len = 0;
				while((len = is.read(buffer,0,8192)) != -1 && !cancelled) {
					fos.write(buffer,0,len);
					//reportTo.sendMessage(reportTo.obtainMessage(MESSAGE_DOWNLOADEDBYTES,len,0));
				}
				
				fos.close();
				is.close();
				
				reportTo.sendEmptyMessage(MESSAGE_FINISHUPDATE);
				
				return;
				
				
			} catch (MalformedURLException e) {
				e.printStackTrace();
				reportTo.sendEmptyMessage(MESSAGE_CANCELDOWNLOAD);
				return;
			} catch (IOException e) {
				e.printStackTrace();
				reportTo.sendEmptyMessage(MESSAGE_CANCELDOWNLOAD);
				return;
			}
			
			
			
			
			
			
			
		}
	}



}