package com.tlongdev.graviti.engine;

import org.andengine.engine.camera.Camera;
import org.andengine.entity.IEntity;
import org.andengine.util.Constants;

public class CustomCamera extends Camera {

    // ===========================================================
    // Constants
    // ===========================================================

    // ===========================================================
    // Fields
    // ===========================================================

    private IEntity mChaseEntity;

    // ===========================================================
    // Constructors
    // ===========================================================

    public CustomCamera(float pX, float pY, float pWidth, float pHeight) {
        super(pX, pY, pWidth, pHeight);
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================

    @Override
    public void setCenter(float pCenterX, float pCenterY) {
        final float dY = pCenterY - this.getCenterY();

        this.mYMin += dY;
        this.mYMax += dY;
    }

    @Override
    public void setChaseEntity(final IEntity pChaseEntity) {
        this.mChaseEntity = pChaseEntity;
    }

    @Override
    public void updateChaseEntity() {
        if (this.mChaseEntity != null) {
            final float[] centerCoordinates = this.mChaseEntity.getSceneCenterCoordinates();
            this.setCenter(centerCoordinates[Constants.VERTEX_INDEX_X],
                           centerCoordinates[Constants.VERTEX_INDEX_Y] + getHeight() / 4);
        }
    }

    // ===========================================================
    // Methods
    // ===========================================================

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
}
