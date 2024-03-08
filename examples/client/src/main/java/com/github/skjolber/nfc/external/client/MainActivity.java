/***************************************************************************
 * 
 * This file is part of the 'External NFC API' project at
 * https://github.com/skjolber/external-nfc-api
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 ****************************************************************************/

package com.github.skjolber.nfc.external.client;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import org.ndeftools.Message;
import org.ndeftools.Record;
import org.ndeftools.UnsupportedRecord;
import org.ndeftools.wellknown.UriRecord;

import android.app.Activity;
import android.app.Fragment;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import com.github.skjolber.android.nfc.Tag;
import com.github.skjolber.android.nfc.tech.IsoDep;
import com.github.skjolber.android.nfc.tech.MifareClassic;
import com.github.skjolber.android.nfc.tech.MifareUltralight;
import com.github.skjolber.android.nfc.tech.Ndef;
import com.github.skjolber.android.nfc.tech.NdefFormatable;
import com.github.skjolber.android.nfc.tech.NfcA;
import com.github.skjolber.android.nfc.tech.NfcB;

import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.github.skjolber.nfc.NfcReader;
import com.github.skjolber.nfc.NfcTag;
import com.github.skjolber.nfc.acs.Acr1222LReader;
import com.github.skjolber.nfc.acs.Acr122UReader;
import com.github.skjolber.nfc.acs.Acr1252UReader;
import com.github.skjolber.nfc.acs.Acr1255UReader;
import com.github.skjolber.nfc.acs.Acr1283LReader;
import com.github.skjolber.nfc.acs.AcrAutomaticPICCPolling;
import com.github.skjolber.nfc.acs.AcrFont;
import com.github.skjolber.nfc.acs.AcrPICC;
import com.github.skjolber.nfc.acs.AcrReader;
import com.github.skjolber.nfc.acs.AcrReaderException;
import com.github.skjolber.nfc.desfire.DesfireReader;
import com.github.skjolber.nfc.desfire.VersionInfo;
import com.github.skjolber.nfc.service.BackgroundUsbService;
import com.github.skjolber.nfc.service.IsoDepDeviceHint;
import com.github.skjolber.nfc.util.CommandAPDU;
import com.github.skjolber.nfc.util.ResponseAPDU;
import com.github.skjolber.nfc.util.activity.NfcExternalDetectorActivity;

/**
 * Simple example implementation. Most of the interesting parts are in the logging output.
 * 
 * @author thomas
 *
 */

public class MainActivity extends NfcExternalDetectorActivity {

	private static final String TAG = MainActivity.class.getName();
	
	protected Boolean service = null;
	protected Boolean reader = null;
	protected Boolean tag = null;
	
	private NdefFormatable ndefFormatable;
	private Ndef ndef;
    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		
		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
		
		initializeExternalNfc();
		
        setDetecting(true);
		createChannel();
		startService();
	}

	void createChannel() {
		NotificationChannel channel = null;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			channel = new NotificationChannel("notificationChannel", "notificationChannel", NotificationManager.IMPORTANCE_HIGH);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			channel.setDescription("Channel for foreground service notification");
		}

		NotificationManager notificationManager = getSystemService(NotificationManager.class);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			notificationManager.createNotificationChannel(channel);
		}
	}

	protected void startService() {
		Intent intent = new Intent();
		intent.setClassName(this, BackgroundUsbService.class.getName());
		ContextCompat.startForegroundService(this,intent);
	}

	protected void stopService() {
		Intent intent = new Intent();
		intent.setClassName(this, BackgroundUsbService.class.getName());
		stopService(intent);
	}
    
	@Override
	public void onResume() {
		super.onResume();		

	}
	
	@Override
	public void onPause() {
		super.onPause();
	}
	
	public void onNfcIntentDetected(Intent intent, String action) {

       	setTagPresent(true);

		if(intent.hasExtra(NfcAdapter.EXTRA_ID)) {
			byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
			Log.d(TAG, "Tag id " + toHexString(id));
			
			setTagId(toHexString(id));
		} else {
			Log.d(TAG, "No tag id");
			
			setTagId(getString(R.string.tagIdNone));
		}

		if(intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
			
			Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (messages != null) {
				Log.d(TAG, "NDEF message");

				NdefMessage[] ndefMessages = new NdefMessage[messages.length];
			    for (int i = 0; i < messages.length; i++) {
			        ndefMessages[i] = (NdefMessage) messages[i];
			    }
			    
		    	// read as much as possible
				Message message = new Message();
				for (int i = 0; i < messages.length; i++) {
			    	NdefMessage ndefMessage = (NdefMessage) messages[i];
			        
					for(NdefRecord ndefRecord : ndefMessage.getRecords()) {
						
						Record record;
						try {
							record = Record.parse(ndefRecord);
							
							Log.d(TAG, "NDEF record " + record.getClass().getName());
						} catch (FormatException e) {
							// if the record is unsupported or corrupted, keep as unsupported record
							record = UnsupportedRecord.parse(ndefRecord);
						}
						
						message.add(record);
					}
			    }
				showRecords(message);
			} else {
				hideRecords();
			}
		} else {
			Log.d(TAG, "No NDEF message");
			
			hideRecords();
		}
		
		setTagType(getString(R.string.tagTypeNone));
		if(intent.hasExtra(NfcAdapter.EXTRA_TAG)) {

			Tag tag = (Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

			try {
				String[] techList = tag.getTechList();
		
				for (String tech : techList) {
					Log.d(TAG, "Tech " + tech);
		
					if (tech.equals(android.nfc.tech.MifareUltralight.class.getName())) {
                        setTagType(getString(R.string.tagTypeMifareUltralight));

                        ByteArrayOutputStream bout = new ByteArrayOutputStream();
                        try {

                            if(intent.hasExtra(NfcTag.EXTRA_ULTRALIGHT_TYPE)) {
                                // handle NTAG21x types
                                // the NTAG21x product familiy have replacements for all previous Ultralight tags
                                int type = intent.getIntExtra(NfcTag.EXTRA_ULTRALIGHT_TYPE, 0);

                                NfcA nfcA = NfcA.get(tag);
                                if(nfcA == null) {
                                    throw new IllegalArgumentException("No NTAG");
                                }

                                int size;
                                switch(type) {
                                    case NfcTag.EXTRA_ULTRALIGHT_TYPE_NTAG210: {
                                        size = 48;
                                        break;
                                    }
                                    case NfcTag.EXTRA_ULTRALIGHT_TYPE_NTAG212: {
                                        size = 128;
                                        break;
                                    }
                                    case NfcTag.EXTRA_ULTRALIGHT_TYPE_NTAG213: {
                                        size = 144;
                                        break;
                                    }
                                    case NfcTag.EXTRA_ULTRALIGHT_TYPE_NTAG215: {
                                        size = 504;
                                        break;
                                    }
                                    case NfcTag.EXTRA_ULTRALIGHT_TYPE_NTAG216 :
                                    case NfcTag.EXTRA_ULTRALIGHT_TYPE_NTAG216F : {
                                        size = 888;
                                        break;
                                    }
                                    default : {
                                        size = 48;
                                    }
                                }
                                int pagesToRead = size / 4 + 4;

                                // instead of reading 4 and 4 pages, read more using the FAST READ command
                                int pagesPerRead;
                                if(nfcA.getMaxTransceiveLength() > 0) {
                                    pagesPerRead = Math.min(255, nfcA.getMaxTransceiveLength() / 4);
                                } else {
                                    pagesPerRead = 255;
                                }

                                int reads = pagesToRead / pagesPerRead;

                                if(pagesToRead % pagesPerRead != 0) {
                                    reads++;
                                }

                                try {
                                    nfcA.connect();
                                    int read = 0;
                                    for (int i = 0; i < reads; i++) {
                                        int range = Math.min(pagesPerRead, pagesToRead - read);

                                        byte[] fastRead = new byte[]{
                                                0x3A,
                                                (byte) (read & 0xFF), // start page
                                                (byte) ((read + range - 1) & 0xFF), // end page (inclusive)
                                        };

                                        bout.write(nfcA.transceive(fastRead));

                                        read += range;
                                    }
                                } finally {
                                    nfcA.close();
                                }


                            } else {
                                MifareUltralight mifareUltralight = MifareUltralight.get(tag);
                                if(mifareUltralight == null) {
                                    throw new IllegalArgumentException("No Mifare Ultralight");
                                }
                                mifareUltralight.connect();

                                int length;

                                int type = mifareUltralight.getType();
                                switch (type) {
                                    case MifareUltralight.TYPE_ULTRALIGHT: {
                                        length = 12;

                                        break;
                                    }
                                    case MifareUltralight.TYPE_ULTRALIGHT_C: {
                                        length = 36;

                                        break;
                                    }
                                    default:
                                        throw new IllegalArgumentException("Unknown mifare ultralight tag " + type);
                                }

                                // android read 4 and 4 pages of 4 bytes
                                for (int i = 0; i < length; i += 4) {
                                    bout.write(mifareUltralight.readPages(i));
                                }
                                mifareUltralight.close();
                            }

							byte[] buffer = bout.toByteArray();
							
							StringBuilder builder = new StringBuilder();
							for(int k = 0; k < buffer.length; k+= 4) {
								builder.append( String.format("%02x", (k / 4)) + " " + toHexString(buffer, k, 4));
								builder.append('\n');
							}
							
							Log.d(TAG, builder.toString());
						} catch(Exception e) {
							Log.d(TAG, "Problem processing tag technology", e);
						}
					} else if (tech.equals(android.nfc.tech.NfcA.class.getName())) {
						NfcA nfcA = NfcA.get(tag);

						byte[] atqa = nfcA.getAtqa();
						short sak = nfcA.getSak();

						Log.d(TAG, "Got NfcA with ATQA " + toHexString(atqa) + " and sak " + Integer.toHexString(sak));
					} else if (tech.equals(android.nfc.tech.NfcB.class.getName())) {
						NfcB nfcB = NfcB.get(tag);

						byte[] applicationData = nfcB.getApplicationData();
						byte[] protocolInfo = nfcB.getProtocolInfo();

						Log.d(TAG, "Got NfcB with application data " + toHexString(applicationData) + " and protcol info " + toHexString(protocolInfo));
					} else if (tech.equals(android.nfc.tech.NfcF.class.getName())) {
						Log.d(TAG, "Ignore " + tech);
					} else if (tech.equals(android.nfc.tech.NfcV.class.getName())) {
						Log.d(TAG, "Ignore " + tech);
					} else if (tech.equals(android.nfc.tech.IsoDep.class.getName())) {
						com.github.skjolber.android.nfc.tech.IsoDep isoDep = IsoDep.get(tag);

						Log.d(TAG, "Historical bytes were '" + toHexString(isoDep.getHistoricalBytes()) + "'");
						if(isoDep.getHiLayerResponse() != null) {
							Log.d(TAG, "HiLayer bytes were '" + toHexString(isoDep.getHiLayerResponse()) + "'");
						}
						Log.d(TAG, "Technologies were " + Arrays.asList(tag.getTechList()) + ", id was " + toHexString(tag.getId()));

						IsoDepDeviceHint hint = new IsoDepDeviceHint(isoDep);

						if(hint.isTag()) {
							Log.d(TAG, "Device hints indicate a Desfire EV1 card");
						} else if(hint.isHostCardEmulation()) {
							Log.d(TAG, "Device hints indicate a Host Card Emulation device");
						} else {
							Log.d(TAG, "Device hints unable to indicate a type");
						}

						boolean hostCardEmulation = intent.getBooleanExtra(NfcTag.EXTRA_HOST_CARD_EMULATION, false);

						if(hostCardEmulation) {
							setTagType(getString(R.string.tagTypeHostCardEmulation));
							
							Log.d(TAG, "Got " + isoDep.getClass().getName() + " for HCE");

				           	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
				           	
					    	boolean autoSelectIsoApplication = prefs.getBoolean(PreferencesActivity.PREFERENCE_HOST_CARD_EMULATION_AUTO_SELECT_ISO_APPLICATION, true);

					    	if(autoSelectIsoApplication) {
								isoDep.connect();

								// attempt to select demo HCE application using iso adpu
						    	String isoApplicationString = prefs.getString(PreferencesActivity.PREFERENCE_HOST_CARD_EMULATION_ISO_APPLICATION_ID, null);
								
						    	// clean whitespace
						    	isoApplicationString = isoApplicationString.replaceAll("\\s","");
						    	
						    	try {
						    		byte[] key = hexStringToByteArray(isoApplicationString);
						    		
						    		// send ISO select application. 
						    		// All commands starting with 0x00 are passed through without ADPU wrapping for HCE 
						    		CommandAPDU command = new CommandAPDU(0x00, 0xA4, 0x04, 00, key);

						    		Log.d(TAG, "Send request " + toHexString(command.getBytes()) );

						    		byte[] responseBytes = isoDep.transceive(command.getBytes());
						    		
						    		Log.d(TAG, "Got response " + toHexString(responseBytes));

						    		ResponseAPDU response = new ResponseAPDU(responseBytes);
						    		
						    		if(response.getSW1() == 0x91 && response.getSW2() == 0x00) {
							    		Log.d(TAG, "Selected HCE application " + isoApplicationString);
							    		
							    		// issue command which now should be routed to the same HCE client
							    		// pretend to select application of desfire card

										boolean pingPong = prefs.getBoolean(PreferencesActivity.PREFERENCE_HOST_CARD_EMULATION_PING_PONG, true);

										if (pingPong) {
											Log.d(TAG, "Invoker will attempt to play ping-pong");
											PingPong.playPingPong(isoDep);
										} else {
											DesfireReader reader = new DesfireReader(isoDep);
											reader.selectApplication(0x00112233);
										}

							    		Log.d(TAG, "Selected application using desfire select application command");
						    		} else if(response.getSW1() == 0x82 && response.getSW2() == 0x6A) {
							    		Log.d(TAG, "HCE application " + isoApplicationString + " not found on remote device");
						    		} else {
							    		Log.d(TAG, "Unknown error selecting HCE application " + isoApplicationString);
						    		}
						    	} catch(Exception e) {
						    		Log.w(TAG, "Unable to decode HEX string " + isoApplicationString + " into binary data", e);
						    	}
								isoDep.close();

					    	}


						} else {
							setTagType(getString(R.string.tagTypeDesfire));

							Log.d(TAG, "Got " + isoDep.getClass().getName());
		
							isoDep.connect();
							
							DesfireReader reader = new DesfireReader(isoDep);
							
							VersionInfo versionInfo = reader.getVersionInfo();
							
							Log.d(TAG, "Got version info - hardware version " + versionInfo.getHardwareVersion() + " / software version " + versionInfo.getSoftwareVersion());
							
							isoDep.close();
						}						
	
					} else if (tech.equals(android.nfc.tech.MifareClassic.class.getName())) {
						com.github.skjolber.android.nfc.tech.MifareClassic mifareClassic = MifareClassic.get(tag);
						
						setTagType(getString(R.string.tagTypeMifareClassic));

						Log.d(TAG, "Got " + mifareClassic.getClass().getName());
					} else if (tech.equals(android.nfc.tech.Ndef.class.getName())) {
						this.ndef = Ndef.get(tag);
						
						Log.d(TAG, "Got " + ndef.getClass().getName());
	
					} else if (tech.equals(android.nfc.tech.NdefFormatable.class.getName())) {
						this.ndefFormatable = NdefFormatable.get(tag);
						
						Log.d(TAG, "Got " + ndefFormatable.getClass().getName());
	
					}
	
				}
			} catch(Exception e) {
				Log.d(TAG, "Problem processing tag technology", e);
			}
		}
		
		invalidateOptionsMenu();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_ndef_format) {
			ndefFormat();
			return true;
		} else if (id == R.id.action_ndef_write) {
			ndefWrite();
			return true;
		} else if(id == R.id.action_preferences) {
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivity(intent);
		}
		
		return super.onOptionsItemSelected(item);
	}


	
	private void ndefWrite() {
		Log.d(TAG, "NDEF write");
		
		Message message = new Message();
		
		UriRecord record = new UriRecord();
		record.setUri(Uri.parse("https://github.com/skjolber/external-nfc-api"));
		
		message.add(record);
		
		try {
			ndef.connect();
			
			ndef.writeNdefMessage(message.getNdefMessage());
			
			ndef.close();
		} catch (Exception e) {
			Log.d(TAG, "Problem writing NDEF message", e);
		}
	}

	private void ndefFormat() {
		Log.d(TAG, "NDEF format write");
		
		Message message = new Message();
		
		UriRecord record = new UriRecord();
		record.setUri(Uri.parse("https://github.com/skjolber/external-nfc-api"));
		
		message.add(record);
		
		try {
			ndefFormatable.connect();
			
			ndefFormatable.format(message.getNdefMessage());
			
			ndefFormatable.close();
		} catch (Exception e) {
			Log.d(TAG, "Problem writing NDEF message", e);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		
		for(int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			if(item.getItemId() == R.id.action_ndef_format) {
				item.setVisible(ndefFormatable != null);
			} else if(item.getItemId() == R.id.action_ndef_write) {
				item.setVisible(ndef != null);
			}
		}
		
		return super.onPrepareOptionsMenu(menu);
	}
	
	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {
		
		public PlaceholderFragment() {
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
						
			return rootView;
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			
		}
		
		@Override
		public void onAttach(Activity activity) {
			super.onAttach(activity);
		}
	}

	public void setServiceStarted(final boolean started) {
		this.service = started;
		
		if(started) {
			setTextViewText(R.id.serviceStatus, R.string.serviceStatusStarted);
			
			setViewVisibility(R.id.readerStatusRow, View.VISIBLE);
		} else {
			setTextViewText(R.id.serviceStatus, R.string.serviceStatusStopped);
			
			setViewVisibility(R.id.readerStatusRow, View.GONE);
		}
		
		invalidateOptionsMenu();
	}
	
	public void setReaderOpen(final boolean open) {
		this.reader = open;
		if(open) {
			setTextViewText(R.id.readerStatus, R.string.readerStatusOpen);
			
			setViewVisibility(R.id.tagStatusRow, View.VISIBLE);
			setViewVisibility(R.id.tagIdRow, View.GONE);
			setViewVisibility(R.id.tagTypeRow, View.GONE);
		} else {
			setTextViewText(R.id.readerStatus, R.string.readerStatusClosed);
			
			setViewVisibility(R.id.tagStatusRow, View.GONE);
			setViewVisibility(R.id.tagIdRow, View.GONE);
			setViewVisibility(R.id.tagTypeRow, View.GONE);
		}
		
		invalidateOptionsMenu();
	}

	private void setViewVisibility(int id, int visibility) {
		View view = findViewById(id);
		view.setVisibility(visibility);
	}

	public void setTagPresent(final boolean present) {
		this.tag = present;
		
		invalidateOptionsMenu();
		
		if(present) {
			setTextViewText(R.id.tagStatus, R.string.tagStatusPresent);
			
			setViewVisibility(R.id.tagIdRow, View.VISIBLE);
			setViewVisibility(R.id.tagTypeRow, View.VISIBLE);
		} else {
			setTextViewText(R.id.tagStatus, R.string.tagStatusAbsent);
			
			setViewVisibility(R.id.tagIdRow, View.GONE);
			setViewVisibility(R.id.tagTypeRow, View.GONE);
			
			hideRecords();
		}
		
		invalidateOptionsMenu();
	}

	public void setTextViewText(final int resource, final int string) {
		setTextViewText(resource, getString(string));
	}

	public void setTagType(final String type) {
		setTextViewText(R.id.tagType, type);
	}

	public void setTagId(final String type) {
		setTextViewText(R.id.tagId, type);
	}

	public void setTextViewText(final int resource, final String string) {
		runOnUiThread(new Runnable() {
			public void run() {
				TextView textView = (TextView) findViewById(resource);
				textView.setText(string);
				textView.setVisibility(View.VISIBLE);
			}
		});
	}

	@Override
	protected void onExternalNfcServiceStopped(Intent intent) {
		setServiceStarted(false);
	}

	@Override
	protected void onExternalNfcServiceStarted(Intent intent) {
		setServiceStarted(true);
	}

	@Override
	protected void onExternalNfcReaderOpened(Intent intent) {
		setReaderOpen(true);
		
		Log.d(TAG, "Reader open");

    	if(intent.hasExtra(NfcReader.EXTRA_READER_CONTROL)) {
    		AcrReader reader = intent.getParcelableExtra(NfcReader.EXTRA_READER_CONTROL);

            try {
                String name = reader.getName();

                String firmware = "unkown";
                try {
                    firmware = reader.getFirmware();
                } catch(Exception e) {
                    Log.d(TAG, "Problem reading firmware", e);
                }
                List<AcrPICC> picc = reader.getPICC();

                Log.d(TAG, "Got reader " + name + " with firmware " + firmware + " and PICC setting " + picc);

                if (reader instanceof Acr122UReader) {
                    Acr122UReader acr122uReader = (Acr122UReader) reader;
                    acr122uReader.setBuzzerForCardDetection(true);

                    acr122uReader.setPICC(
                            AcrPICC.AUTO_PICC_POLLING,
                            AcrPICC.POLL_ISO14443_TYPE_B,
                            AcrPICC.POLL_ISO14443_TYPE_A,
                            AcrPICC.AUTO_ATS_GENERATION
                    );

                    //acr122uReader.setPICC(AcrPICC.AUTO_PICC_POLLING, AcrPICC.POLL_ISO14443_TYPE_B, AcrPICC.POLL_ISO14443_TYPE_A);
                } else if (reader instanceof Acr1222LReader) {
                    Acr1222LReader acr1222lReader = (Acr1222LReader) reader;

                    // display font example - note that also font type C
                    acr1222lReader.lightDisplayBacklight(true);
                    acr1222lReader.clearDisplay();
                    acr1222lReader.displayText(AcrFont.FontA, Typeface.BOLD, 0, 0, "Hello ACR1222L!");
                    acr1222lReader.displayText(AcrFont.FontB, Typeface.BOLD, 1, 0, "ABCDE 0123456789");
                } else if (reader instanceof Acr1283LReader) {
                    Acr1283LReader acr1283LReader = (Acr1283LReader) reader;

                    // display font example - note that also font type C
                    acr1283LReader.lightDisplayBacklight(true);
                    acr1283LReader.clearDisplay();
                    acr1283LReader.displayText(AcrFont.FontA, Typeface.BOLD, 0, 0, "Hello ACR1283L!");
                    acr1283LReader.displayText(AcrFont.FontB, Typeface.BOLD, 1, 0, "ABCDE 0123456789");
                } else if (reader instanceof Acr1252UReader) {
                    Acr1252UReader acr1252UReader = (Acr1252UReader) reader;
                    acr1252UReader.setPICC(
                            AcrPICC.POLL_ISO14443_TYPE_B,
                            AcrPICC.POLL_ISO14443_TYPE_A
                    );
                    acr1252UReader.setAutomaticPICCPolling(AcrAutomaticPICCPolling.AUTO_PICC_POLLING, AcrAutomaticPICCPolling.ACTIVATE_PICC_WHEN_DETECTED, AcrAutomaticPICCPolling.ENFORCE_ISO14443A_PART_4);
                } else if (reader instanceof Acr1255UReader) {


					Acr1255UReader bluetoothReader = (Acr1255UReader) reader;

					Log.d(TAG, "Battery level is " + bluetoothReader.getBatteryLevel() + "%");

					bluetoothReader.setPICC(AcrPICC.POLL_ISO14443_TYPE_A, AcrPICC.POLL_ISO14443_TYPE_B);

					bluetoothReader.setAutomaticPICCPolling(AcrAutomaticPICCPolling.AUTO_PICC_POLLING, AcrAutomaticPICCPolling.ENFORCE_ISO14443A_PART_4, AcrAutomaticPICCPolling.PICC_POLLING_INTERVAL_1000);
					bluetoothReader.setAutomaticPolling(true);

                    // XXX this seems to put the reader in a sort of bricked state
                    //acr1255UReader.setSleepModeOption(-1); // no sleep
                }
            } catch(AcrReaderException e) {
                Log.d(TAG, "Problem accessing reader", e);
            }
    	} else {
    		Log.d(TAG, "No reader supplied");
    	}
	}

	@Override
	protected void onExternalNfcReaderClosed(Intent intent) {
    	if(intent.hasExtra(NfcReader.EXTRA_READER_STATUS_CODE)) {
    		Log.d(TAG, "Disconnect status code " + intent.getIntExtra(NfcReader.EXTRA_READER_STATUS_CODE, -1));
    	}

    	if(intent.hasExtra(NfcReader.EXTRA_READER_STATUS_MESSAGE)) {
    		Log.d(TAG, "Disconnect status message " + intent.getCharSequenceExtra(NfcReader.EXTRA_READER_STATUS_MESSAGE));
    	}

		setReaderOpen(false);
	}

   /**
     * 
     * NFC feature was found and is currently enabled
     * 
     */
	
	@Override
	protected void onNfcStateEnabled() {
		toast(getString(R.string.nfcAvailableEnabled));
	}

    /**
     * 
     * NFC feature was found but is currently disabled
     * 
     */
	
	@Override
	protected void onNfcStateDisabled() {
		toast(getString(R.string.nfcAvailableDisabled));
	}

	/**
     * 
     * NFC setting changed since last check. For example, the user enabled NFC in the wireless settings.
     * 
     */
	
	@Override
	protected void onNfcStateChange(boolean enabled) {
		if(enabled) {
			toast(getString(R.string.nfcAvailableEnabled));
		} else {
			toast(getString(R.string.nfcAvailableDisabled));
		}
	}

	/**
	 * 
	 * This device does not have NFC hardware
	 * 
	 */
	
	@Override
	protected void onNfcFeatureNotFound() {
		toast(getString(R.string.noNfcMessage));
	}
	
	@Override
	protected void onNfcTagLost(Intent intent) {
		setTagPresent(false);
		
		invalidateOptionsMenu();
	}
    
	protected void onExternalNfcTagLost(Intent intent) {
		// default to same as native NFC
		this.ndef = null;
		this.ndefFormatable = null;

		onNfcTagLost(intent);
	}
	
	protected void onExternalNfcIntentDetected(Intent intent, String action) {
		// default to same as native NFC
		onNfcIntentDetected(intent, action);
	}

	public void toast(int id) {
		toast(getString(id));
	}
	
	public void toast(String message) {
		Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
		toast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL, 0, 0);
		toast.show();
	}
	
	/**
	 * 
	 * Show NDEF records in the list
	 * 
	 */
	
	private void showRecords(Message message) {
		// display the message in the gui
		
		ListView listView = (ListView) findViewById(R.id.recordListView);
		View ndefRecords = findViewById(R.id.ndefRecords);
		if(message != null && !message.isEmpty()) {
			Log.d(TAG, "Show " + message.size() + " records");

			ArrayAdapter<? extends Object> adapter = new NdefRecordAdapter(this, message);
			listView.setAdapter(adapter);
			listView.setVisibility(View.VISIBLE);
			
			ndefRecords.setVisibility(View.VISIBLE);
		} else {
			listView.setVisibility(View.GONE);
			
			ndefRecords.setVisibility(View.GONE);
		}
		
	}
	
	private void hideRecords() {
		ListView listView = (ListView) findViewById(R.id.recordListView);
		View ndefRecords = findViewById(R.id.ndefRecords);
		
		listView.setVisibility(View.GONE);
		ndefRecords.setVisibility(View.GONE);
	}

	public static byte[] hexStringToByteArray(String s) {
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}


}
