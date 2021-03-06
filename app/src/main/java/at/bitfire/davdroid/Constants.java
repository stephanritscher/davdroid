/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid;

import net.fortuna.ical4j.model.property.ProdId;

public class Constants {
	public static final String
		APP_VERSION = "0.6.9",
		ACCOUNT_TYPE = "bitfire.at.davdroid.mirakel", /* modified because mirakel refuses original account type */
		WEB_URL_HELP = "https://davdroid.bitfire.at/configuration?pk_campaign=davdroid-app";
	public static final ProdId PRODUCT_ID = new ProdId("-//bitfire web engineering//DAVdroid " + Constants.APP_VERSION + " (ical4j 2.0.x)//EN");
}
