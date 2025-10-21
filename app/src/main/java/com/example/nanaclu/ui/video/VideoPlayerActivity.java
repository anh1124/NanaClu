package com.example.nanaclu.ui.video;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nanaclu.R;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import java.util.Locale;

public class VideoPlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private String videoUrl;
    private String postId;
    
    // Custom controls
    private ImageButton btnPlayPause;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvDuration;
    private ImageButton btnMute;
    private ImageButton btnFullscreen;
    private ProgressBar progressBar;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBar;
    private boolean isFullscreen = false;
    private boolean isMuted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        
        // Get video URL and post ID from intent
        videoUrl = getIntent().getStringExtra("videoUrl");
        postId = getIntent().getStringExtra("postId");
        
        if (videoUrl == null) {
            Toast.makeText(this, "Video URL not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        initializePlayer();
        setupCustomControls();
        
        // Keep screen on while playing
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    private void initViews() {
        playerView = findViewById(R.id.playerView);
        progressBar = findViewById(R.id.progressBar);
        
        // Custom controls
        btnPlayPause = findViewById(R.id.btnPlayPause);
        seekBar = findViewById(R.id.seekBar);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvDuration = findViewById(R.id.tvDuration);
        btnMute = findViewById(R.id.btnMute);
        btnFullscreen = findViewById(R.id.btnFullscreen);
    }
    
    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        
        MediaItem mediaItem = MediaItem.fromUri(videoUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
        
        // Set up player listeners
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        progressBar.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_READY:
                        progressBar.setVisibility(View.GONE);
                        updateDuration();
                        startSeekBarUpdates();
                        break;
                    case Player.STATE_ENDED:
                        stopSeekBarUpdates();
                        btnPlayPause.setImageResource(R.drawable.ic_play_arrow_24);
                        break;
                }
            }
            
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_24 : R.drawable.ic_play_arrow_24);
                if (isPlaying) {
                    startSeekBarUpdates();
                } else {
                    stopSeekBarUpdates();
                }
            }
            
            @Override
            public void onPlayerError(PlaybackException error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(VideoPlayerActivity.this, "Error playing video: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        // Start playing automatically
        player.setPlayWhenReady(true);
    }
    
    private void setupCustomControls() {
        // Play/Pause button
        btnPlayPause.setOnClickListener(v -> {
            if (player.getPlaybackState() == Player.STATE_ENDED) {
                player.seekTo(0);
            }
            player.setPlayWhenReady(!player.getPlayWhenReady());
        });
        
        // Seek bar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player.getDuration() > 0) {
                    long position = (long) (progress * player.getDuration() / 100.0);
                    player.seekTo(position);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopSeekBarUpdates();
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                startSeekBarUpdates();
            }
        });
        
        // Mute button
        btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            player.setVolume(isMuted ? 0f : 1f);
            btnMute.setImageResource(isMuted ? R.drawable.ic_volume_off_24 : R.drawable.ic_volume_up_24);
        });
        
        // Fullscreen button
        btnFullscreen.setOnClickListener(v -> toggleFullscreen());
    }
    
    private void updateDuration() {
        if (player.getDuration() > 0) {
            tvDuration.setText(formatTime(player.getDuration()));
        }
    }
    
    private void startSeekBarUpdates() {
        stopSeekBarUpdates();
        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.getDuration() > 0) {
                    long currentPosition = player.getCurrentPosition();
                    long duration = player.getDuration();
                    
                    tvCurrentTime.setText(formatTime(currentPosition));
                    seekBar.setProgress((int) (currentPosition * 100 / duration));
                    
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(updateSeekBar);
    }
    
    private void stopSeekBarUpdates() {
        if (updateSeekBar != null) {
            handler.removeCallbacks(updateSeekBar);
            updateSeekBar = null;
        }
    }
    
    private String formatTime(long timeMs) {
        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
    
    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        
        if (isFullscreen) {
            // Enter fullscreen
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (getSupportActionBar() != null) getSupportActionBar().hide();
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit_24);
        } else {
            // Exit fullscreen
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (getSupportActionBar() != null) getSupportActionBar().show();
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen_24);
        }
    }
    
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isFullscreen = true;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (getSupportActionBar() != null) getSupportActionBar().hide();
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit_24);
        } else {
            isFullscreen = false;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (getSupportActionBar() != null) getSupportActionBar().show();
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen_24);
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.pause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSeekBarUpdates();
        if (player != null) {
            player.release();
            player = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            toggleFullscreen();
        } else {
            super.onBackPressed();
        }
    }
}
