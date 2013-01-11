/* shadowsocksproxy - GAppProxy / WallProxy client App for Android
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class ShadowsocksProxyReceiver extends BroadcastReceiver {
	
	private String proxy;
	
	private boolean isAutoConnect = false;
	
	private boolean isInstalled = false;
	
	private boolean isGlobalProxy = false;
	
	private static final String TAG = "ShadowsocksProxy";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		String versionName;
		try {
			versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			versionName = "NONE";
		}
		
		isAutoConnect = settings.getBoolean("isAutoConnect", false);
		isInstalled = settings.getBoolean(versionName, false);
		
		if (isAutoConnect && isInstalled) {
			proxy = settings.getString("proxy", "");
			
			String passwd = settings.getString("passwd", "");
			
			isGlobalProxy = settings.getBoolean("isGlobalProxy", false);
			
			Intent it = new Intent(context, ShadowsocksProxyService.class);
			Bundle bundle = new Bundle();
			bundle.putString("proxy", proxy);
			bundle.putString("passwd", passwd);
			bundle.putBoolean("isGlobalProxy", isGlobalProxy);
			
			it.putExtras(bundle);
			context.startService(it);
		}
	}
	
}
