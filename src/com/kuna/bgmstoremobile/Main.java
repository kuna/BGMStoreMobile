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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_main);
        mContext = this;

        // set event handler
        final ListView lv = (ListView) findViewById(R.id.listView1);
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

        Button b;
        b = (Button) findViewById(R.id.btnrandom);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Handler h = new Handler() {
                    @Override
                    public void dispatchMessage(Message msg) {
                        mSongData = (SongData) msg.obj;
                        Log.i("RANDOM", mSongData.title);
                        Log.i("RANDOM", mSongData.url);
                        playMusic(mSongData.url);

                        super.dispatchMessage(msg);
                    }
                };
                BGMStoreParser.getRandomSong(h);
            }
        });

        b = (Button) findViewById(R.id.btnsearch);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Handler h = new Handler() {
                    @Override
                    public void dispatchMessage(Message msg) {
                        mSongDataList = (List<SongData>) msg.obj;
                        ListAdapter la = new ListAdapter(mContext, mSongDataList);
                        lv.setAdapter(la);

                        super.dispatchMessage(msg);
                    }
                };

                TextView tv = (TextView) findViewById(R.id.search);
                String search = tv.getText().toString();
                if (search.length() > 0) {
                    BGMStoreQuery.setKeyword(search);
                    BGMStoreParser.parseBGMStoreList(h);
                }
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
                        ListAdapter la = new ListAdapter(mContext, mSongDataList);
                        lv.setAdapter(la);

                        super.dispatchMessage(msg);
                    }
                };

                BGMStoreQuery.setKeyword("");
                BGMStoreParser.parseBGMStoreList(h);
            }
        });
    }

    public void playMusic(String url) {
        if (mAudioStreamer != null) {
            mAudioStreamer.interrupt();
        }

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
                    }

                    super.dispatchMessage(msg);
                }
            };

            mAudioStreamer = new StreamingMediaPlayer(this, h);
            mAudioStreamer.startStreaming(url);
            mAudioStreamer.setLoop(true);

            playButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
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
}
