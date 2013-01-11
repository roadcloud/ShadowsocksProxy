/* shadowsocksproxy - GoAgent / WallProxy client App for Android
 * Copyright (C) 2011 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package org.shadowsocks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.shadowsocks.db.DNSResponse;
import org.shadowsocks.db.DatabaseHelper;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.flurry.android.FlurryAgent;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

public class ShadowsocksProxy extends PreferenceActivity implements
        OnSharedPreferenceChangeListener {
	
	public static final String SETTING_REMOTEDNS = "remotedns";
	
	private static final String TAG = "ShadowsocksProxy";
	
	public static final String PREFS_NAME = "ShadowsocksProxy";
	
	private String proxy;
	
	private String port;
	
	private String passwd;
	
	private String remoteDNS;
	
	private boolean isGlobalProxy = false;
	
	private static final int MSG_CRASH_RECOVER = 1;
	
	private static final int MSG_INITIAL_FINISH = 2;
	
	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			SharedPreferences settings = PreferenceManager
			        .getDefaultSharedPreferences(ShadowsocksProxy.this);
			Editor ed = settings.edit();
			switch (msg.what) {
				case MSG_CRASH_RECOVER:
					Toast.makeText(ShadowsocksProxy.this, R.string.crash_alert, Toast.LENGTH_LONG)
					        .show();
					ed.putBoolean("isRunning", false);
					break;
				case MSG_INITIAL_FINISH:
					if (pd != null) {
						pd.dismiss();
						pd = null;
					}
					break;
			}
			ed.commit();
			super.handleMessage(msg);
		}
	};
	
	private static ProgressDialog pd = null;
	
	private CheckBoxPreference isAutoConnectCheck;
	
	private CheckBoxPreference isGlobalProxyCheck;
	
	private EditTextPreference proxyText;
	
	private EditTextPreference portText;
	
	private EditTextPreference passwdText;
	
	private EditTextPreference remoteDNSText;
	
	private CheckBoxPreference isRunningCheck;
	
	// private AdView adView;
	private Preference proxyedApps;
	
	private void copyAssets(String path) {
		
		AssetManager assetManager = getAssets();
		String[] files = null;
		try {
			files = assetManager.list(path);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
		for (int i = 0; i < files.length; i++) {
			InputStream in = null;
			OutputStream out = null;
			try {
				in = assetManager.open(files[i]);
				out = new FileOutputStream("/data/data/org.shadowsocks/" + files[i]);
				copyFile(in, out);
				in.close();
				in = null;
				out.flush();
				out.close();
				out = null;
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}
	}
	
	private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
	}
	
	private void crash_recovery() {
		
		Utils.runRootCommand(Utils.getIptables() + " -t nat -F OUTPUT");
		
		Utils.runCommand(ShadowsocksProxyService.BASE + "proxy.sh stop");
		
	}
	
	private void dirChecker(String dir) {
		File f = new File(dir);
		
		if (!f.isDirectory()) {
			f.mkdirs();
		}
	}
	
	private void disableAll() {
		proxyText.setEnabled(false);
		passwdText.setEnabled(false);
		portText.setEnabled(false);
		remoteDNSText.setEnabled(false);
		
		proxyedApps.setEnabled(false);
		
		isAutoConnectCheck.setEnabled(false);
		isGlobalProxyCheck.setEnabled(false);
	}
	
	private void enableAll() {
		proxyText.setEnabled(true);
		passwdText.setEnabled(true);
		portText.setEnabled(true);
		remoteDNSText.setEnabled(true);
		
		if (!isGlobalProxyCheck.isChecked())
			proxyedApps.setEnabled(true);
		
		isGlobalProxyCheck.setEnabled(true);
		isAutoConnectCheck.setEnabled(true);
	}
	
	private boolean install() {
		
		PowerManager.WakeLock mWakeLock;
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
		        | PowerManager.ON_AFTER_RELEASE, "ShadowsocksProxy");
		
		String data_path = Utils.getDataPath(this);
		
		try {
			final InputStream pythonZip = getAssets().open("modules/python.mp3");
			final InputStream extraZip = getAssets().open("modules/python-extras.mp3");
			
			unzip(pythonZip, "/data/data/org.shadowsocks/");
			unzip(extraZip, data_path + "/");
		} catch (IOException e) {
			Log.e(TAG, "unable to install python");
		}
		if (mWakeLock.isHeld())
			mWakeLock.release();
		
		return true;
	}
	
	private boolean isTextEmpty(String s, String msg) {
		if (s == null || s.length() <= 0) {
			showAToast(msg);
			return true;
		}
		return false;
	}
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main);
		addPreferencesFromResource(R.xml.shadowsocks_proxy_preference);
		
		proxyText = (EditTextPreference) findPreference("proxy");
		passwdText = (EditTextPreference) findPreference("passwd");
		portText = (EditTextPreference) findPreference("port");
		remoteDNSText = (EditTextPreference) findPreference(SETTING_REMOTEDNS);
		proxyedApps = findPreference("proxyedApps");
		
		isRunningCheck = (CheckBoxPreference) findPreference("isRunning");
		isAutoConnectCheck = (CheckBoxPreference) findPreference("isAutoConnect");
		isGlobalProxyCheck = (CheckBoxPreference) findPreference("isGlobalProxy");
		
		if (pd == null)
			pd = ProgressDialog.show(this, "", getString(R.string.initializing), true, true);
		
		final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		
		new Thread() {
			@Override
			public void run() {
				
				Utils.isRoot();
				
				String versionName;
				try {
					versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
				} catch (NameNotFoundException e) {
					versionName = "NONE";
				}
				
				if (!settings.getBoolean(versionName, false)) {
					
					Editor edit = settings.edit();
					edit.putBoolean(versionName, true);
					edit.commit();
					
					File f = new File("/data/data/org.shadowsocks/certs");
					if (f.exists() && f.isFile())
						f.delete();
					if (!f.exists())
						f.mkdir();
					
					copyAssets("");
					
					Utils.runCommand("chmod 755 /data/data/org.shadowsocks/iptables\n"
					        + "chmod 755 /data/data/org.shadowsocks/redsocks\n"
					        + "chmod 755 /data/data/org.shadowsocks/proxy.sh\n"
					        + "chmod 755 /data/data/org.shadowsocks/python-cl\n");
					
					install();
					
				}
				
				if (!(new File(Utils.getDataPath(ShadowsocksProxy.this) + "/python-extras"))
				        .exists()) {
					install();
				}
				
				handler.sendEmptyMessage(MSG_INITIAL_FINISH);
			}
		}.start();
	}
	
	// 点击Menu时，系统调用当前Activity的onCreateOptionsMenu方法，并传一个实现了一个Menu接口的menu对象供你使用
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		/*
		 * add()方法的四个参数，依次是： 1、组别，如果不分组的话就写Menu.NONE,
		 * 2、Id，这个很重要，Android根据这个Id来确定不同的菜单 3、顺序，那个菜单现在在前面由这个参数的大小决定
		 * 4、文本，菜单的显示文本
		 */
		menu.add(Menu.NONE, Menu.FIRST + 1, 1, getString(R.string.recovery)).setIcon(
		        android.R.drawable.ic_menu_delete);
		menu.add(Menu.NONE, Menu.FIRST + 2, 2, getString(R.string.about)).setIcon(
		        android.R.drawable.ic_menu_info_details);
		// return true才会起作用
		return true;
		
	}
	
	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean("isConnected", ShadowsocksProxyService.isServiceStarted());
		editor.commit();
		
		if (pd != null) {
			pd.dismiss();
			pd = null;
		}
		
		// adView.destroy();
		
		super.onDestroy();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) { // 按下的如果是BACK，同时没有重复
			try {
				finish();
			} catch (Exception ignore) {
				// Nothing
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
	
	// 菜单项被选择事件
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case Menu.FIRST + 1:
				recovery();
				break;
			case Menu.FIRST + 2:
				String versionName = "";
				try {
					versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
				} catch (NameNotFoundException e) {
					versionName = "";
				}
				showAToast(getString(R.string.about) + " (" + versionName + ")\n\n"
				        + getString(R.string.copy_rights));
				break;
		}
		
		return true;
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		// Unregister the listener whenever a key changes
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
		        this);
	}
	
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		
		if (preference.getKey() != null && preference.getKey().equals("proxyedApps")) {
			Intent intent = new Intent(this, AppManager.class);
			startActivity(intent);
		} else if (preference.getKey() != null && preference.getKey().equals("browser")) {
			Intent intent = new Intent(this, org.shadowsocks.zirco.ui.activities.MainActivity.class);
			startActivity(intent);
		} else if (preference.getKey() != null && preference.getKey().equals("isRunning")) {
			if (!serviceStart()) {
				Editor edit = settings.edit();
				edit.putBoolean("isRunning", false);
				edit.commit();
			}
		}
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		
		if (settings.getBoolean("isGlobalProxy", false))
			proxyedApps.setEnabled(false);
		else
			proxyedApps.setEnabled(true);
		
		Editor edit = settings.edit();
		
		if (ShadowsocksProxyService.isServiceStarted()) {
			edit.putBoolean("isRunning", true);
		} else {
			if (settings.getBoolean("isRunning", false)) {
				new Thread() {
					@Override
					public void run() {
						crash_recovery();
						handler.sendEmptyMessage(MSG_CRASH_RECOVER);
					}
				}.start();
			}
			edit.putBoolean("isRunning", false);
		}
		
		edit.commit();
		
		if (settings.getBoolean("isRunning", false)) {
			isRunningCheck.setChecked(true);
			disableAll();
		} else {
			isRunningCheck.setChecked(false);
			enableAll();
		}
		
		// Setup the initial values
		if (!settings.getString("port", "").equals(""))
			portText.setSummary(settings.getString("port", "8388"));
		
		if (!settings.getString(SETTING_REMOTEDNS, "").equals("")) {
			remoteDNSText.setSummary(settings.getString(SETTING_REMOTEDNS, "8000"));
		}
		
		if (!settings.getString("proxy", "").equals(""))
			proxyText.setSummary(settings.getString("proxy", getString(R.string.proxy_summary)));
		
		// Set up a listener whenever a key changes
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// Let's do something a preference value changes
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		
		if (key.equals("isConnecting")) {
			if (settings.getBoolean("isConnecting", false)) {
				Log.d(TAG, "Connecting start");
				if (pd == null)
					pd = ProgressDialog.show(this, "", getString(R.string.connecting), true, true);
			} else {
				Log.d(TAG, "Connecting finish");
				if (pd != null) {
					pd.dismiss();
					pd = null;
				}
			}
		}
		
		if (key.equals("isGlobalProxy")) {
			if (settings.getBoolean("isGlobalProxy", false))
				proxyedApps.setEnabled(false);
			else
				proxyedApps.setEnabled(true);
		}
		
		if (key.equals("isRunning")) {
			if (settings.getBoolean("isRunning", false)) {
				disableAll();
				isRunningCheck.setChecked(true);
			} else {
				isRunningCheck.setChecked(false);
				enableAll();
			}
		}
		
		if (key.equals("proxy")) {
			if (settings.getString("proxy", "").equals("")) {
				proxyText.setSummary(getString(R.string.proxy_summary));
			} else {
				proxyText.setSummary(settings.getString("proxy", ""));
			}
		} else if (key.equals("port")) {
			if (settings.getString("port", "").equals("")) {
				portText.setSummary("8388");
			} else {
				portText.setSummary(settings.getString("port", ""));
			}
		} else if (key.equals(SETTING_REMOTEDNS)) {
			if (settings.getString(SETTING_REMOTEDNS, "").equals("")) {
				remoteDNSText.setSummary("8000");
			} else {
				remoteDNSText.setSummary(settings.getString(SETTING_REMOTEDNS, ""));
			}
		}
		
	}
	
	@Override
	public void onStart() {
		super.onStart();
		FlurryAgent.onStartSession(this, "46W95Q7YQQ6IY1NFIQW4");
	}
	
	@Override
	public void onStop() {
		super.onStop();
		FlurryAgent.onEndSession(this);
	}
	
	private void recovery() {
		
		if (pd == null)
			pd = ProgressDialog.show(this, "", getString(R.string.recovering), true, true);
		
		final Handler h = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				if (pd != null) {
					pd.dismiss();
					pd = null;
				}
			}
		};
		
		try {
			stopService(new Intent(this, ShadowsocksProxyService.class));
		} catch (Exception e) {
			// Nothing
		}
		
		new Thread() {
			@Override
			public void run() {
				
				Utils.runRootCommand(Utils.getIptables() + " -t nat -F OUTPUT");
				
				Utils.runCommand(ShadowsocksProxyService.BASE + "proxy.sh stop");
				
				try {
					DatabaseHelper helper = OpenHelperManager.getHelper(ShadowsocksProxy.this,
					        DatabaseHelper.class);
					Dao<DNSResponse, String> dnsCacheDao = helper.getDNSCacheDao();
					List<DNSResponse> list = dnsCacheDao.queryForAll();
					for (DNSResponse resp : list) {
						dnsCacheDao.delete(resp);
					}
				} catch (Exception ignore) {
					// Nothing
				}
				
				copyAssets("");
				
				Utils.runCommand("chmod 755 /data/data/org.shadowsocks/iptables\n"
				        + "chmod 755 /data/data/org.shadowsocks/redsocks\n"
				        + "chmod 755 /data/data/org.shadowsocks/proxy.sh\n"
				        + "chmod 755 /data/data/org.shadowsocks/python-cl\n");
				
				install();
				
				h.sendEmptyMessage(0);
			}
		}.start();
		
	}
	
	/**
	 * Called when connect button is clicked.
	 * 
	 * @throws Exception
	 */
	public boolean serviceStart() {
		
		if (ShadowsocksProxyService.isServiceStarted()) {
			try {
				stopService(new Intent(this, ShadowsocksProxyService.class));
			} catch (Exception e) {
				// Nothing
			}
			return false;
		}
		
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		
		proxy = settings.getString("proxy", "");
		if (isTextEmpty(proxy, getString(R.string.proxy_empty)))
			return false;
		
		port = settings.getString("port", "8388");
		
		passwd = settings.getString("passwd", "");
		remoteDNS = settings.getString(SETTING_REMOTEDNS, "8000");
		
		isGlobalProxy = settings.getBoolean("isGlobalProxy", false);
		
		try {
			Intent it = new Intent(this, ShadowsocksProxyService.class);
			Bundle bundle = new Bundle();
			bundle.putString("proxy", proxy);
			bundle.putString("passwd", passwd);
			bundle.putString("port", port);
			bundle.putString(SETTING_REMOTEDNS, remoteDNS);
			bundle.putBoolean("isGlobalProxy", isGlobalProxy);
			
			it.putExtras(bundle);
			startService(it);
		} catch (Exception e) {
			// Nothing
			return false;
		}
		
		return true;
	}
	
	private void showAToast(String msg) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(msg)
		        .setCancelable(false)
		        .setNegativeButton(getString(R.string.ok_iknow),
		                new DialogInterface.OnClickListener() {
			                @Override
			                public void onClick(DialogInterface dialog, int id) {
				                dialog.cancel();
			                }
		                });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	public void unzip(InputStream zip, String path) {
		dirChecker(path);
		try {
			ZipInputStream zin = new ZipInputStream(zip);
			ZipEntry ze = null;
			while ((ze = zin.getNextEntry()) != null) {
				if (ze.getName().contains("__MACOSX"))
					continue;
				// Log.v("Decompress", "Unzipping " + ze.getName());
				if (ze.isDirectory()) {
					dirChecker(path + ze.getName());
				} else {
					FileOutputStream fout = new FileOutputStream(path + ze.getName());
					byte data[] = new byte[10 * 1024];
					int count;
					while ((count = zin.read(data)) != -1) {
						fout.write(data, 0, count);
					}
					zin.closeEntry();
					fout.close();
				}
				
			}
			zin.close();
		} catch (Exception e) {
			Log.e("Decompress", "unzip", e);
		}
	}
	
}
