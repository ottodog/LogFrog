package com.gmail.dajinchu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class MainGame implements Screen, InputProcessor, SavedGameListener{
    final ScreenManager sm;

    //prefs
    private final boolean signedin;
    public static boolean hints, linkAnimation;


    SpriteBatch batch;
    Model model;
    private ShapeRenderer renderer;
    Stage uistage;
    public Table infotable;

    static float nodeRadius=5, logWidth=6;
    static int mapHeight, mapWidth;
    private OrthographicCamera cam;

    static{
        mapWidth = (int) (4*20+nodeRadius*2);
        mapHeight = (int) (6*20+nodeRadius*2);
    }


    public int level;
    Viewport viewport;

    public static AnalyticsHelper ah;
    public final SavedGameHelper sgh;
    public TextButton options;
    private GameView view;
    Label levelinfo;
    private Table optionmenu;
    private GameController controller;

    public MainGame(ScreenManager sm, AnalyticsHelper ah, SavedGameHelper sgh) {
        this.ah=ah;
        this.sgh=sgh;
        this.sm = sm;
        level = sm.prefs.getInteger("level",1);
        hints = sm.prefs.getBoolean("hints", true);
        signedin = sm.prefs.getBoolean("gpgs", false);
        linkAnimation = sm.prefs.getBoolean("link_animation", true);
    }

    @Override
	public void show () {
		batch = sm.batch;
        renderer = sm.renderer;

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        viewport = new FitViewport(mapWidth, mapHeight);
        cam= new OrthographicCamera();
        viewport.setCamera(cam);

        uistage = sm.uistage;
        uistage.clear();

        makeInfoTable();
        makeOptionsMenu();

        //This draws the actual gameplay
        view = new GameView(this);

        //This manages inputs
        controller = new GameController();

        sm.multiplexer.addProcessor(this);

        //Now that levelinfo is instantiated, it is safe to load level
        sgh.load(this);
        loadLevel();
	}

    public void makeInfoTable(){
        //info table resides in a sort of upper bar above the gameplay, and has the options button
        //and level/moves info
        infotable = new Table();
        infotable.setFillParent(true);
        infotable.top();
        options = new TextButton("Options", sm.buttonStyle);
        options.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if(!optionmenu.isVisible())event.stop();
                optionmenu.setVisible(true);

                final InputListener optionlistener = new InputListener(){
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
                        //Prevents events that are for the options menu from leaking to the game
                        //listener or the tapping-away-from-menu-box listener
                        event.stop();
                        return true;
                    }
                };
                optionmenu.addListener(optionlistener);
                uistage.addListener(new InputListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        //Closes the options menu and unregisters these two listeners when anything
                        //is clicked, except if it is the menu box getting clicked, in which case
                        //the event is stopped by the previous listener
                        optionmenu.setVisible(false);
                        uistage.removeListener(this);
                        optionmenu.removeListener(optionlistener);
                        return false;
                    }
                });
                return false;
            }
        });
        levelinfo = new Label("Level "+level+"  Moves: 0",sm.labelStyle);

        infotable.pad(10);
        infotable.add(levelinfo).left().top().expandX();
        infotable.add(options).right().top().expandX();
        uistage.addActor(infotable);
    }

    public void makeOptionsMenu(){
        //option table is the menu overlay that pops up after hitting the options button
        optionmenu = new Table();
        optionmenu.setFillParent(true);
        optionmenu.center();
        Stack optionstack = new Stack();
        VerticalGroup options = new VerticalGroup();
        Image optionbackground = new Image(sm.buttonStyle.up);
        Label optiontitle = new Label("Options",sm.labelStyle);
        final TextButton resetlevel =  new TextButton("Reset Level", sm.buttonStyle);
        final TextButton togglehints = new TextButton("",sm.buttonStyle);
        final TextButton toggleanimation = new TextButton("",sm.buttonStyle);
        TextButton sync = new TextButton(" Sync Play Games ", sm.buttonStyle);
        TextButton mainmenu = new TextButton("Main Menu", sm.buttonStyle);

        resetlevel.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                loadLevel();
            }
        });
        updatePrefText(togglehints, "Hints", hints);
        togglehints.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if(hints){
                    hints = false;
                }else{
                    hints = true;
                }
                updatePrefText(togglehints, "Hints", hints);
                sm.prefs.putBoolean("hints", hints);
            }
        });
        updatePrefText(toggleanimation, "Animations", linkAnimation);
        toggleanimation.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if(linkAnimation){
                    linkAnimation = false;
                }else{
                    linkAnimation = true;
                }
                updatePrefText(toggleanimation, "Animations", linkAnimation);
                sm.prefs.putBoolean("link_animation", linkAnimation);
            }
        });
        sync.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                sgh.load(MainGame.this);
            }
        });
        mainmenu.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                sm.mainmenu();
            }
        });
        options.pad(20);
        options.space(20);
        options.fill();
        options.center();
        options.addActor(optiontitle);
        options.addActor(resetlevel);
        options.addActor(togglehints);
        options.addActor(toggleanimation);
        if(signedin)options.addActor(sync);
        options.addActor(mainmenu);

        optionstack.add(optionbackground);
        optionstack.add(options);
        optionmenu.add(optionstack);
        optionmenu.setVisible(false);
        uistage.addActor(optionmenu);
    }

    @Override
	public void render (float delta) {
        viewport.apply();

        renderer.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
		Gdx.gl.glClearColor(1,1,1,1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if(model==null){
            return;
        }

        view.draw(batch, renderer);

        //.apply changes the "active" viewport. SO IMPORTANT, and NOT ON DOCUMENTATION
        sm.uiviewport.apply();
        uistage.act(Gdx.graphics.getDeltaTime());
        uistage.draw();
    }

    @Override
    public void resize(int w, int h){
        viewport.update(w, (int) (h - options.getHeight() - infotable.getPadBottom() - infotable.getPadTop()), true);
        cam.zoom=1.1f;

        /*
        float translate =viewport.unproject(new Vector3(0,h-viewport.project(new Vector2(0,mapHeight)).y,0)).y;
        Gdx.app.log("Main", "translate "+translate+"height "+h+" map "+viewport.project(new Vector2(0,mapHeight)).y);
        viewport.getCamera().translate(0,5,0);
        viewport.getCamera().update();*/
    }

    private void updatePrefText(TextButton toggle, String display, boolean preference){
        if(preference) {
            toggle.setText(display+": On");
        }else{
            toggle.setText(display+": Off");
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if(keycode== Input.Keys.BACK){
            sm.mainmenu();
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Vector3 v = viewport.unproject(new Vector3(screenX,screenY,0));
        Rectangle clickBox = new Rectangle(v.x,v.y,1,1);

        controller.touchDown(clickBox,model,linkAnimation,view);
        if(model.isLevelBeaten())nextLevel();
        levelinfo.setText("Level " + level + "  Moves: " + model.movesToComplete);
        return true;
    }

    public void nextLevel(){
        ah.sendCustomTiming("User Interaction", (int) TimeUtils.timeSinceMillis(model.loadedMillis),
                "Time to finish a level", "Level "+level);

        ah.sendEvent("Level Balance",
                "Moves to finish a level", "Level "+level, model.movesToComplete);

        level++;
        sgh.write(new byte[]{(byte) level});
        sgh.setStepsAchievement("CgkIldTq9O0NEAIQBQ",level);
        sm.prefs.putInteger("level", level);
        loadLevel();

    }

    protected void loadLevel(){
        if(level == 41) {
            sm.setScreen(new End(sm, this));
            return;
        }
        model = new Model(Gdx.files.internal("level"+level+".txt"));
        levelinfo.setText("Level "+level+"  Moves: 0");
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        return false;
    }

    @Override
    public void pause(){
        //sgh.write(new byte[]{(byte) level});

    }

    @Override
    public void resume(){

    }

    @Override
    public void dispose() {

    }

    @Override
    public void hide() {
        sm.multiplexer.removeProcessor(this);
    }

    public void forceLevel(int level){
        this.level = level;
        sgh.write(new byte[]{(byte) level});
        loadLevel();
    }

    @Override
    public int resolveConflict(byte[] data0, byte[] data1){
        int level0 = data0[0], level1 = data1[0];
        if(level0>=level1){
            return 0;
        }else{
            return 1;
        }
    }

    @Override
    public void onGameLoad(byte[] data) {
        if(level==data[0]){
            //Local and cloud copy are the same, don't load level
            return;
        }
        if(level>data[0]){
            //local copy is ahead of cloud. update the cloud version
            sgh.write(new byte[]{(byte) level});
        }else{
            //catch up with cloud copy
            level = data[0];
            loadLevel();
            sm.prefs.putInteger("level", level);
        }

    }
}
