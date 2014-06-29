package com.kuna.bgmstoremobile;

import java.net.URLDecoder;
import java.util.List;

import android.support.v4.app.Fragment;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.os.Build;

public class Main extends Activity {
	private StreamingMediaPlayer audioStreamer;
	
	private SongData ndata;
	private Context c;
	private List<SongData> lsd;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.fragment_main);
		c = this;
		
		// set event handler
		final ListView lv = (ListView)findViewById(R.id.listView1);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (lsd != null) {
					ndata = lsd.get(position);
					PlayMusic(ndata.url);
				}
			}
		});
		
		Button b;
		b = (Button)findViewById(R.id.btnrandom);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Handler h = new Handler() {
					@Override
					public void dispatchMessage(Message msg) {
						ndata = (SongData) msg.obj;
						Log.i("RANDOM", ndata.title);
						Log.i("RANDOM", ndata.url);
						PlayMusic(ndata.url);
					
						super.dispatchMessage(msg);
					}
				};
				BGMStoreParser.getRandomSong(h);
			}
		});

		b = (Button)findViewById(R.id.btnsearch);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Handler h = new Handler() {
					@Override
					public void dispatchMessage(Message msg) {
						lsd = (List<SongData>) msg.obj;
						ListAdapter la = new ListAdapter(c, lsd);
						lv.setAdapter(la);
					
						super.dispatchMessage(msg);
					}
				};
				
				TextView tv = (TextView)findViewById(R.id.search);
				String search = tv.getText().toString();
				if (search.length() > 0) {
					BGMStoreQuery.q = search;
					BGMStoreParser.parseBGMStoreList(h);
				}
			}
		});

		b = (Button)findViewById(R.id.btnrecent);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Handler h = new Handler() {
					@Override
					public void dispatchMessage(Message msg) {
						lsd = (List<SongData>) msg.obj;
						ListAdapter la = new ListAdapter(c, lsd);
						lv.setAdapter(la);
					
						super.dispatchMessage(msg);
					}
				};
				
				BGMStoreQuery.q = "";
				BGMStoreParser.parseBGMStoreList(h);
			}
		});
	}
	
	public void PlayMusic(String url) {
		if (audioStreamer != null) {
			audioStreamer.interrupt();
		}
		
		final SeekBar seekbar = (SeekBar)findViewById(R.id.seekbar);
		final TextView textStreamed = (TextView)findViewById(R.id.nowplaying);
		final Button playButton = (Button)findViewById(R.id.button_play);
		final Button downButton = (Button)findViewById(R.id.button_down);
		
		textStreamed.setText(ndata.title);
		downButton.setEnabled(false);

		downButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();
				String mp3name = ndata.url.substring(ndata.url.indexOf("mp3/")+4);
				mp3name = URLDecoder.decode(mp3name);
				path += "/" + mp3name + ".mp3";
				
				audioStreamer.downloadFileTo(path);
				Toast.makeText(c, "Downloaded to - " + path, Toast.LENGTH_LONG).show();
			}
		});
		
		seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				audioStreamer.setSeek((double)seekBar.getProgress() / 100.0);
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
			}
		});
		
		try {
			Handler h = new Handler() {
				@Override
				public void dispatchMessage(Message msg) {
					switch (msg.what) {
					case StreamingMediaPlayer.MSG_PLAYING:
						seekbar.setProgress((Integer)msg.obj);
						break;
					case StreamingMediaPlayer.MSG_READY:
						break;
					case StreamingMediaPlayer.MSG_DOWNLOADED:
						downButton.setEnabled(true);
						break;
					}
					
					
					super.dispatchMessage(msg);
				}
			};
			
			audioStreamer = new StreamingMediaPlayer(this, h);
			audioStreamer.startStreaming(url);
			audioStreamer.setLoop(true);

			playButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					if (audioStreamer.getMediaPlayer().isPlaying()) {
						audioStreamer.getMediaPlayer().pause();
						//playButton.setImageResource(R.drawable.button_play);
					} else {
						audioStreamer.getMediaPlayer().start();
						audioStreamer.startPlayProgressUpdater();
						//playButton.setImageResource(R.drawable.button_pause);
					}
	        }});
		} catch (Exception e) {
			e.printStackTrace();
		}
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
		if (id == R.id.info) {
			Intent intent = new Intent(this, Info.class);
			startActivity(intent);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) { // 세로 전환시 발생
		} else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) { // 가로 전환시 발생
		}
		//super.onConfigurationChanged(newConfig);
	}
}
