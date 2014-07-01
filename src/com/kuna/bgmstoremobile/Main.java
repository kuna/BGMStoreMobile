package com.kuna.bgmstoremobile;

import java.net.URLDecoder;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class Main extends Activity {
    private StreamingMediaPlayer mAudioStreamer;

    private SongData mSongData;
    private Context mContext;
    private List<SongData> mSongDataList;
    private ListView lv;
    private ListAdapter la;
    private boolean mInfinteRandom = false;
    private boolean mListViewLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // to use progress...
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);  
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_main);
        mContext = this;
        
        // load settings
        Settings.LoadSettings(this);
        
        // set music intent receiver
        Handler h = new Handler() {
        	@Override
        	public void dispatchMessage(Message msg) {
        		if (msg.what == 0) {
        			if (Settings.PauseWhenPlugout && mAudioStreamer != null) {
        				mAudioStreamer.getMediaPlayer().pause();
        			}
        		}
        		super.dispatchMessage(msg);
        	}
        };
        MusicIntentReceiver mReceiver = new MusicIntentReceiver(this, h);

        // set event handler
        lv = (ListView) findViewById(R.id.listView1);
        lv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                if (mSongDataList != null) {
                    mSongData = mSongDataList.get(position);
                    playMusic(mSongData.url);
                }
            }
        });
        lv.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				
			}
			
			// implement Endless scroll
			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
		        if (!mListViewLoading && mSongDataList != null && mSongDataList.size() > 0) {
		        	int lastInScreen = firstVisibleItem + visibleItemCount;
		            if (lastInScreen == mSongDataList.size()) {
		                Handler h = new Handler() {
		                    @Override
		                    public void dispatchMessage(Message msg) {
		                    	List<SongData> data = (List<SongData>) msg.obj;
		                        if (data.size() == 0) {
		                        	Toast.makeText(mContext, "End of List!", Toast.LENGTH_LONG).show();
		                            setProgressBarIndeterminateVisibility(false);
		                        	return;
		                        }
		                        mSongDataList.addAll(data);
		                        la.setData(mSongDataList);
		                        la.notifyDataSetChanged();

		                        mListViewLoading = false;
		                        setProgressBarIndeterminateVisibility(false);
		                        super.dispatchMessage(msg);
		                    }
		                };

		                BGMStoreQuery.setLimitCount( BGMStoreQuery.getLimitCount()+1 );
		                BGMStoreParser.parseBGMStoreList(h);
		                mListViewLoading = true;
                        setProgressBarIndeterminateVisibility(true);
		            }
		        }
			}
		});

        Button b;
        b = (Button) findViewById(R.id.btnrandom);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	doRandomPlay(false);
            }
        });

        final EditText etext = (EditText)findViewById(R.id.search);
        etext.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
		           InputMethodManager imm = 
		                   (InputMethodManager)mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
		           imm.hideSoftInputFromWindow(etext.getWindowToken(), 0);
		           doQuerySearch(etext.getText().toString());
		           return true;
				}
				return false;
			}
		});
        
        b = (Button) findViewById(R.id.btnsearch);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	doQuerySearch(etext.getText().toString());
            }
        });

        b = (Button) findViewById(R.id.btnrecent);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Handler h = new Handler() {
                    @Override
                    public void dispatchMessage(Message msg) {
                        mSongDataList = (List<SongData>) msg.obj;
                        la = new ListAdapter(mContext, mSongDataList);
                        lv.setAdapter(la);
                        setProgressBarIndeterminateVisibility(false);

                        super.dispatchMessage(msg);
                    }
                };

                BGMStoreQuery.setKeyword("");
                BGMStoreQuery.setLimitCount(0);
                BGMStoreParser.parseBGMStoreList(h);
                setProgressBarIndeterminateVisibility(true);
            }
        });
    }
    
    public void playMusic(String url) {
    	playMusic(url, true);
    }

    public void playMusic(String url, boolean setLoop) {
        if (mAudioStreamer != null) {
            mAudioStreamer.interrupt();
        }
        
        // default: infinite random is always of without inquiring.
        mInfinteRandom = false;
        		
        final SeekBar seekbar = (SeekBar) findViewById(R.id.seekbar);
        final TextView textStreamed = (TextView) findViewById(R.id.nowplaying);
        final Button playButton = (Button) findViewById(R.id.button_play);
        final Button downButton = (Button) findViewById(R.id.button_down);

        textStreamed.setText(mSongData.title);
        downButton.setEnabled(false);

        downButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();
                String mp3name = mSongData.url.substring(mSongData.url.indexOf("mp3/") + 4);
                mp3name = URLDecoder.decode(mp3name);
                path += "/" + mp3name + ".mp3";

                mAudioStreamer.downloadFileTo(path);
                Toast.makeText(mContext, "Downloaded to - " + path, Toast.LENGTH_LONG).show();
            }
        });

        seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            	if (mAudioStreamer.getMediaPlayer() == null)
            		return;
                mAudioStreamer.setSeek((double) seekBar.getProgress() / 100.0);
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
                        seekbar.setProgress((Integer) msg.obj);
                        break;
                    case StreamingMediaPlayer.MSG_READY:
                        break;
                    case StreamingMediaPlayer.MSG_DOWNLOADED:
                        downButton.setEnabled(true);
                        break;
                    case StreamingMediaPlayer.MSG_COMPLETED:
                    	if (mInfinteRandom) {
                        	doRandomPlay(true);
                    	}
                    	break;
                    }

                    super.dispatchMessage(msg);
                }
            };

            mAudioStreamer = new StreamingMediaPlayer(this, h);
            mAudioStreamer.startStreaming(url);
            mAudioStreamer.setLoop(setLoop);

            playButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                	if (mAudioStreamer.getMediaPlayer() == null)
                		return;
                	
                    if (mAudioStreamer.getMediaPlayer().isPlaying()) {
                        mAudioStreamer.getMediaPlayer().pause();
                        //playButton.setImageResource(R.drawable.button_play);
                    } else {
                        mAudioStreamer.getMediaPlayer().start();
                        mAudioStreamer.startPlayProgressUpdater();
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
        MenuItem mi = menu.findItem(R.id.plugout);
        mi.setChecked(Settings.PauseWhenPlugout);
        return true;
    }
    
    private void doQuerySearch(String str) {
        if (str.length() == 0) 
        	return;
        
        Handler h = new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                mSongDataList = (List<SongData>) msg.obj;
                la = new ListAdapter(mContext, mSongDataList);
                lv.setAdapter(la);
                setProgressBarIndeterminateVisibility(false);

                super.dispatchMessage(msg);
            }
        };

        BGMStoreQuery.setKeyword(str);
        BGMStoreQuery.setLimitCount(0);
        BGMStoreParser.parseBGMStoreList(h);
        setProgressBarIndeterminateVisibility(true);
    }
    
    private void doRandomPlay(final boolean doinfinite) {
        Handler h = new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                mSongData = (SongData) msg.obj;
                if (doinfinite) {
                    playMusic(mSongData.url, false);
                    mInfinteRandom = true;	// enquire infinite random play
                } else {
                	playMusic(mSongData.url);
                }

                setProgressBarIndeterminateVisibility(false);
                super.dispatchMessage(msg);
            }
        };
        BGMStoreParser.getRandomSong(h);
        setProgressBarIndeterminateVisibility(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.plugout) {
    		Settings.PauseWhenPlugout = !Settings.PauseWhenPlugout;
    		item.setChecked(Settings.PauseWhenPlugout);
        } else if (id == R.id.random) {
        	doRandomPlay(true);
        } else if (id == R.id.info) {
            Intent intent = new Intent(this, Info.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
