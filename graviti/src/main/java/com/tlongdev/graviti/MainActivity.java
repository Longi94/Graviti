package com.tlongdev.graviti;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.OnInvitationReceivedListener;
import com.google.example.games.basegameutils.GameHelper;
import com.tlongdev.graviti.service.BackgroundMusicService;


public class MainActivity extends Activity implements GameHelper.GameHelperListener, GoogleApiClient.ConnectionCallbacks,
        OnInvitationReceivedListener {

    // ===========================================================
    // Constants
    // ===========================================================

    public final int CLIENT_GAMES = GameHelper.CLIENT_GAMES;

    private static final int RC_LEADERBOARD = 12349;
    private static final int RC_INVITATION_INBOX = 12347;
    private static final int RC_ACHIEVEMENTS = 12348;

    // ===========================================================
    // Fields
    // ===========================================================

    private BackgroundMusicService mBackgroundMusic;

    private boolean mBound = false;
    private boolean mDebugLog = false;

    // The game helper object. This class is mainly a wrapper around this object.
    private GameHelper mGameHelper;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBackgroundMusic = ((BackgroundMusicService.LocalBinder)service).getService();
            mBackgroundMusic.setVolume(1.0f);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    private TextView mAccountTextView;
    private TextView mScoreTextView;
    private TextView mScoreRushTextView;

    // ===========================================================
    // Constructors
    // ===========================================================

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    protected GoogleApiClient getApiClient() {
        return mGameHelper.getApiClient();
    }

    public GameHelper getGameHelper() {
        if (mGameHelper == null) {
            mGameHelper = new GameHelper(this, CLIENT_GAMES);
            mGameHelper.enableDebugLog(mDebugLog);
            mGameHelper.setMaxAutoSignInAttempts(0);
        }
        return mGameHelper;
    }

    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================


    @Override
    protected void onCreate(Bundle pSavedInstanceState) {
        super.onCreate(pSavedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().getDecorView().setBackgroundColor(Color.WHITE);

        if (mGameHelper == null) {
            getGameHelper();
        }
        if (mGameHelper != null) {
            mGameHelper.setup(this);
        }

        final TextView normalTextView = (TextView) findViewById(R.id.normal_text_view);
        TextView rushTextView = (TextView) findViewById(R.id.rush_text_view);
        TextView multiPlayerTextView = (TextView) findViewById(R.id.multiplayer_text_view);
        mAccountTextView = (TextView)findViewById(R.id.google_account_text_view);
        mScoreTextView = (TextView)findViewById(R.id.score_text_view);
        mScoreRushTextView = (TextView)findViewById(R.id.score_rush_text_view);

        ImageView leaderBoardButton = (ImageView) findViewById(R.id.leaderboards_button);
        ImageView invitationButton = (ImageView)findViewById(R.id.invitations_button);
        ImageView achievementsButton = (ImageView)findViewById(R.id.achievements_button);
        final ImageView refreshButton = (ImageView)findViewById(R.id.refresh_button);
        final ImageView musicButton = (ImageView)findViewById(R.id.music_button);

        normalTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, PlayGameActivity.class);
                intent.putExtra("signed_in", isSignedIn());
                startActivity(intent);
            }
        });

        rushTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RushGameActivity.class);
                startActivity(intent);
            }
        });

        multiPlayerTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!isSignedIn()) {
                    Toast.makeText(MainActivity.this, "You're not signed in", Toast.LENGTH_SHORT).show();
                    return;
                }

                CharSequence[] levels = {"Easy", "Hard"};
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Pick mode")
                        .setItems(levels, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent;
                                switch (which) {
                                    case 0:
                                        intent = new Intent(MainActivity.this, MultiPlayerEasyActivity.class);
                                        break;
                                    case 1:
                                        intent = new Intent(MainActivity.this, MultiPlayerHardActivity.class);
                                        break;
                                    default:
                                        intent = new Intent(MainActivity.this, MultiPlayerEasyActivity.class);
                                        break;
                                }
                                startActivity(intent);
                            }
                        });
                builder.create();
                builder.show();
            }
        });

        leaderBoardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSignedIn())
                    startActivityForResult(Games.Leaderboards.getAllLeaderboardsIntent(getApiClient()), RC_LEADERBOARD);
                else {
                    Toast.makeText(MainActivity.this, "You're not signed in", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if(isSignedIn()){
            mAccountTextView.setText(getResources().getString(R.string.google_sign_out));
        }
        mAccountTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isSignedIn()) {
                    beginUserInitiatedSignIn();
                } else {
                    signOut();
                    mAccountTextView.setText(getResources().getString(R.string.google_sign_in));
                }
            }
        });

        if (getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE).
                getBoolean(getString(R.string.pref_music_key), true))
            musicButton.setImageResource(R.drawable.ic_action_volume_on);
        else
            musicButton.setImageResource(R.drawable.ic_action_volume_muted);

        musicButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toggleMusic()){
                    musicButton.setImageResource(R.drawable.ic_action_volume_on);
                } else {
                    musicButton.setImageResource(R.drawable.ic_action_volume_muted);
                }
            }
        });

        invitationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isSignedIn()) {
                    Toast.makeText(MainActivity.this, "You're not signed in", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = Games.Invitations.getInvitationInboxIntent(getApiClient());
                startActivityForResult(intent, RC_INVITATION_INBOX);
            }
        });

        achievementsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSignedIn()) {
                    Intent intent = Games.Achievements.getAchievementsIntent(getApiClient());
                    startActivityForResult(intent, RC_ACHIEVEMENTS);
                }
                else {
                    Toast.makeText(MainActivity.this, "You're not signed in", Toast.LENGTH_SHORT).show();
                }
            }
        });

        refreshButton.setOnClickListener(new View.OnClickListener() {

            private boolean normalRunning = true;
            private boolean rushRunning = true;
            private int normalScore = 0;
            private int rushScore = 0;

            @Override
            public void onClick(View v) {
                if (!isSignedIn()) {
                    Toast.makeText(MainActivity.this, "You're not signed in", Toast.LENGTH_SHORT).show();
                    return;
                }

                refreshButton.startAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.refresh));

                Games.Leaderboards.loadCurrentPlayerLeaderboardScore(getApiClient(),
                        getString(R.string.leaderboard_normal),
                        LeaderboardVariant.TIME_SPAN_ALL_TIME,
                        LeaderboardVariant.COLLECTION_PUBLIC)
                        .setResultCallback(new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {

                            @Override
                            public void onResult(Leaderboards.LoadPlayerScoreResult result) {
                                int lScore;
                                if (result.getScore() != null) {
                                    lScore = (int) result.getScore().getRawScore();
                                }
                                else {
                                    lScore = 0;
                                }

                                SharedPreferences prefs = getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE);
                                int pScore = prefs.getInt(getString(R.string.pref_highscore_key), 0);

                                if (lScore > pScore){
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putInt(getString(R.string.pref_highscore_key), lScore);
                                    editor.apply();
                                    normalScore = lScore;
                                }
                                else {
                                    Games.Leaderboards.submitScore(getApiClient(),
                                            getString(R.string.leaderboard_normal), pScore);
                                    normalScore = pScore;
                                }

                                normalRunning = false;
                                if (!rushRunning) {
                                    refreshButton.clearAnimation();
                                    updateScores();
                                }
                            }
                        });

                Games.Leaderboards.loadCurrentPlayerLeaderboardScore(getApiClient(),
                        getString(R.string.leaderboard_rush),
                        LeaderboardVariant.TIME_SPAN_ALL_TIME,
                        LeaderboardVariant.COLLECTION_PUBLIC)
                        .setResultCallback(new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {

                            @Override
                            public void onResult(Leaderboards.LoadPlayerScoreResult result) {
                                int lScore;
                                if (result.getScore() != null) {
                                    lScore = (int) result.getScore().getRawScore();
                                }
                                else {
                                    lScore = 0;
                                }

                                SharedPreferences prefs = getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE);
                                int pScore = prefs.getInt(getString(R.string.pref_highscore_rush_key), 0);

                                if (lScore > pScore){
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putInt(getString(R.string.pref_highscore_rush_key), lScore);
                                    editor.apply();
                                    rushScore = lScore;
                                }
                                else {
                                    Games.Leaderboards.submitScore(getApiClient(),
                                            getString(R.string.leaderboard_rush), pScore);
                                    rushScore = pScore;
                                }

                                rushRunning = false;
                                if (!normalRunning) {
                                    refreshButton.clearAnimation();
                                    updateScores();
                                }
                            }

                        });

            }

            private void updateScores() {
                mScoreTextView.setText("" + normalScore);
                mScoreRushTextView.setText("" + rushScore);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGameHelper.onStart(this);
    }

    @Override
    protected synchronized void onResume() {
        super.onResume();
        if (!mBound) {
            bindService(new Intent(this, BackgroundMusicService.class), mConnection, Context.BIND_AUTO_CREATE);
        }

        mScoreTextView.setText("" + getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE)
                .getInt(getString(R.string.pref_highscore_key), 0));
        mScoreRushTextView.setText("" + getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE)
                .getInt(getString(R.string.pref_highscore_rush_key), 0));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        mGameHelper.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mGameHelper.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Games.Invitations.registerInvitationListener(getApiClient(), this);

        if (connectionHint != null) {
            Invitation inv =
                    connectionHint.getParcelable(Multiplayer.EXTRA_INVITATION);

            if (inv != null) {
                // accept invitation
                Intent intent = new Intent(MainActivity.this, MultiPlayerEasyActivity.class);
                intent.putExtra("invitation_id", inv.getInvitationId());

                // TODO go to game screen
            }
        }
    }

    @Override
    public void onInvitationReceived(Invitation invitation) {
        // TODO  show in-game popup to let user know of pending invitation

        // TODO store invitation for use when player accepts this invitation
        String mIncomingInvitationId = invitation.getInvitationId();
    }

    @Override
    public void onInvitationRemoved(String s) {

    }

    @Override
    public void onSignInFailed() {
    }

    @Override
    public void onSignInSucceeded() {
        mAccountTextView.setText(getResources().getString(R.string.google_sign_out));
        Games.setViewForPopups(getApiClient(), getWindow().getDecorView().findViewById(android.R.id.content));
    }
    // ===========================================================
    // Methods
    // ===========================================================

    protected void beginUserInitiatedSignIn() {
        mGameHelper.beginUserInitiatedSignIn();
    }

    protected void enableDebugLog(boolean enabled) {
        mDebugLog = true;
        if (mGameHelper != null) {
            mGameHelper.enableDebugLog(enabled);
        }
    }

    protected String getInvitationId() {
        return mGameHelper.getInvitationId();
    }

    protected GameHelper.SignInFailureReason getSignInError() {
        return mGameHelper.getSignInError();
    }

    protected boolean hasSignInError() {
        return mGameHelper.hasSignInError();
    }

    protected boolean isSignedIn() {
        return mGameHelper.isSignedIn();
    }

    protected void reconnectClient() {
        mGameHelper.reconnectClient();
    }

    protected void showAlert(String message) {
        mGameHelper.makeSimpleDialog(message).show();
    }

    protected void showAlert(String title, String message) {
        mGameHelper.makeSimpleDialog(title, message).show();
    }

    protected void signOut() {
        mGameHelper.signOut();
    }

    private boolean toggleMusic() {
        SharedPreferences.Editor editor = getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE).edit();
        if (getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE).
                getBoolean(getString(R.string.pref_music_key), true)) {
            editor.putBoolean(getString(R.string.pref_music_key), false);
            editor.apply();
            mBackgroundMusic.stopMusic();
            return false;
        }
        else {
            editor.putBoolean(getString(R.string.pref_music_key), true);
            editor.apply();
            mBackgroundMusic.startMusic();
            return true;
        }
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
}
