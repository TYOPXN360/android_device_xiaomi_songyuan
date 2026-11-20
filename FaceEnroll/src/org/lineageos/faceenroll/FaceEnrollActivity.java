/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.faceenroll;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.hardware.biometrics.BiometricFaceConstants;
import android.hardware.face.FaceEnrollOptions;
import android.hardware.face.FaceManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.UserHandle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Face enrollment where the HAL owns the camera and draws the preview.
 */
public class FaceEnrollActivity extends Activity {

    private static final String TAG = "FaceEnroll";

    private static final String EXTRA_KEY_CHALLENGE_TOKEN = "hw_auth_token";
    private static final String EXTRA_KEY_REQUIRE_VISION = "accessibility_vision";
    private static final String EXTRA_KEY_REQUIRE_DIVERSITY = "accessibility_diversity";
    private static final String EXTRA_FINISHED_ENROLL_FACE = "finished_enrolling_face";
    private static final int RESULT_FINISHED = RESULT_FIRST_USER;
    private static final int RESULT_SKIP = RESULT_FIRST_USER + 1;
    private static final int RESULT_TIMEOUT = RESULT_FIRST_USER + 2;

    private static final int PREVIEW_WIDTH = 480;
    private static final int PREVIEW_HEIGHT = 640;

    private FaceManager mFaceManager;
    private CancellationSignal mCancel;
    private byte[] mToken;
    private int mUserId;
    private int mTotalSteps = -1;
    private boolean mDone;

    private TextureView mTextureView;
    private Surface mPreviewSurface;
    private TextView mProgressText;
    private TextView mHelpText;
    private Button mButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFaceManager = getSystemService(FaceManager.class);
        mToken = getIntent().getByteArrayExtra(EXTRA_KEY_CHALLENGE_TOKEN);
        mUserId = getIntent().getIntExtra(Intent.EXTRA_USER_ID, UserHandle.myUserId());
        if (mFaceManager == null || mToken == null) {
            Log.e(TAG, "Missing FaceManager or auth token");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        setContentView(buildLayout());
    }

    private View buildLayout() {
        final int pad = dp(24);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, dp(96), pad, pad);
        root.setFitsSystemWindows(true);

        final TextView title = new TextView(this);
        title.setText(R.string.enroll_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        final int size = dp(280);
        final FrameLayout circle = new FrameLayout(this);
        circle.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        circle.setClipToOutline(true);
        final LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(size, size);
        circleParams.topMargin = dp(48);
        root.addView(circle, circleParams);

        mTextureView = new TextureView(this);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        circle.addView(mTextureView, new FrameLayout.LayoutParams(size, size));

        mProgressText = new TextView(this);
        mProgressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mProgressText.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = dp(32);
        root.addView(mProgressText, progressParams);

        mHelpText = new TextView(this);
        mHelpText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mHelpText.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        helpParams.topMargin = dp(16);
        root.addView(mHelpText, helpParams);

        mButton = new Button(this);
        mButton.setText(R.string.enroll_skip);
        mButton.setOnClickListener(v -> finishWith(mDone ? RESULT_FINISHED : RESULT_SKIP));
        root.addView(mButton);
        return root;
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            texture.setDefaultBufferSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
            final Matrix matrix = new Matrix();
            matrix.setScale(1f, (float) PREVIEW_HEIGHT / PREVIEW_WIDTH,
                    width / 2f, height / 2f);
            mTextureView.setTransform(matrix);
            mPreviewSurface = new Surface(texture);
            startEnrollment();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    private void startEnrollment() {
        if (mCancel != null) {
            return;
        }
        final List<Integer> disabled = new ArrayList<>();
        if (!getIntent().getBooleanExtra(EXTRA_KEY_REQUIRE_DIVERSITY, true)) {
            disabled.add(BiometricFaceConstants.FEATURE_REQUIRE_REQUIRE_DIVERSITY);
        }
        if (!getIntent().getBooleanExtra(EXTRA_KEY_REQUIRE_VISION, true)) {
            disabled.add(BiometricFaceConstants.FEATURE_REQUIRE_ATTENTION);
        }
        final int[] disabledFeatures = disabled.stream().mapToInt(Integer::intValue).toArray();

        mCancel = new CancellationSignal();
        mFaceManager.enroll(mUserId, mToken, mCancel, mEnrollmentCallback, disabledFeatures,
                mPreviewSurface, false /* debugConsent */,
                new FaceEnrollOptions.Builder().build());
    }

    private final FaceManager.EnrollmentCallback mEnrollmentCallback =
            new FaceManager.EnrollmentCallback() {
        @Override
        public void onEnrollmentProgress(int remaining) {
            if (mTotalSteps == -1) {
                mTotalSteps = remaining + 1;
            }
            final int percent = mTotalSteps > 0
                    ? 100 * (mTotalSteps - remaining) / mTotalSteps : 0;
            mProgressText.setText(getString(R.string.enroll_progress, percent));
            if (remaining == 0) {
                mDone = true;
                mHelpText.setText(null);
                mButton.setText(R.string.enroll_done);
                finishWith(RESULT_FINISHED);
            }
        }

        @Override
        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
            if (helpString != null) {
                mHelpText.setText(helpString);
            }
        }

        @Override
        public void onEnrollmentError(int errMsgId, CharSequence errString) {
            if (errMsgId == BiometricFaceConstants.FACE_ERROR_CANCELED) {
                return;
            }
            Log.w(TAG, "Enrollment error " + errMsgId + ": " + errString);
            mHelpText.setText(errString);
            mCancel = null;
            if (errMsgId == BiometricFaceConstants.FACE_ERROR_TIMEOUT) {
                finishWith(RESULT_TIMEOUT);
            }
        }
    };

    private void finishWith(int result) {
        if (isFinishing()) {
            return;
        }
        final Intent data = new Intent();
        data.putExtra(EXTRA_FINISHED_ENROLL_FACE, mDone);
        setResult(result, data);
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCancel != null && !mDone) {
            mCancel.cancel();
        }
        if (!isChangingConfigurations() && !isFinishing()) {
            finishWith(mDone ? RESULT_FINISHED : RESULT_CANCELED);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreviewSurface != null) {
            mPreviewSurface.release();
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
