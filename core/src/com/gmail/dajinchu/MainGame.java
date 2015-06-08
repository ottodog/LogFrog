package com.gmail.dajinchu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class MainGame implements InputProcessor, Screen {
    private final ScreenManager sm;
    SpriteBatch batch;
    Model model;
    private ShapeRenderer renderer;
    private ShapeRenderer debugRenderer;
    private Stage uistage;
    private Table table;

    static float nodeRadius=4, logWidth=4;
    static int mapHeight, mapWidth;


    Preferences prefs;
    public int level = 1;
    private Viewport viewport;

    public static AnalyticsHelper ah;
    private final SavedGameHelper sgh;
    private TextButton sync;
    private GameView view;
    private ScreenViewport uiviewport;
    private Label levelinfo;

    public MainGame(ScreenManager sm, AnalyticsHelper ah, SavedGameHelper sgh) {
        this.ah=ah;
        this.sgh=sgh;
        this.sm = sm;
        sgh.load(this);

    }

    @Override
	public void show () {
		batch = new SpriteBatch();
        renderer = new ShapeRenderer();
        debugRenderer = new ShapeRenderer();

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        mapWidth = (int) (4*20+nodeRadius*2);
        mapHeight = (int) (6*20+nodeRadius*2);
        viewport = new FitViewport(mapWidth, mapHeight);

        uiviewport = new ScreenViewport();
        uistage = new Stage();
        table = new Table();
        table.setFillParent(true);
        table.top();
        sync = new TextButton("Sync", sm.buttonStyle);
        sync.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                sgh.load(MainGame.this);
            }
        });
        levelinfo = new Label("Level "+level+"  Moves: 0",sm.labelStyle);

        table.add(levelinfo).left().top().expandX();
        table.add(sync).right().top().expandX();
        uistage.addActor(table);
        view = new GameView(this);


        //Now that levelinfo is instantiated, it is safe to load level
        loadLevel();

        /*prefs=Gdx.app.getPreferences("My Prefs");
        level = prefs.getInteger("level",1);*/
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uistage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);
	}

    @Override
	public void render (float delta) {
        Gdx.app.log("Main", ""+viewport.getScreenHeight());
        viewport.apply();

        renderer.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        debugRenderer.setProjectionMatrix(viewport.getCamera().combined);
		Gdx.gl.glClearColor(0f, 0f, 0f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if(model==null){
            return;
        }
        batch.begin();
        view.draw(batch);
        batch.end();

        //.apply changes the "active" viewport. SO IMPORTANT, and NOT ON DOCUMENTATION
        uiviewport.apply();
        uistage.act(Gdx.graphics.getDeltaTime());
        uistage.draw();

    }

    @Override
    public void resize(int w, int h){
        Gdx.app.log("Main", w + " " + h+" sync "+sync.getHeight());
        uiviewport.update(w,h,true);
        viewport.update(w, (int) (h - sync.getHeight()), true);
        Gdx.app.log("Main", "" + viewport.getScreenHeight());
        Gdx.app.log("Main", "" + viewport.getScreenHeight());
        uistage.setViewport(uiviewport);
        Gdx.app.log("Main", "" + viewport.getScreenHeight());
        if(viewport.getCamera()==uiviewport.getCamera()) Gdx.app.log("Main", "camera");

        /*
        float translate =viewport.unproject(new Vector3(0,h-viewport.project(new Vector2(0,mapHeight)).y,0)).y;
        Gdx.app.log("Main", "translate "+translate+"height "+h+" map "+viewport.project(new Vector2(0,mapHeight)).y);
        viewport.getCamera().translate(0,5,0);
        viewport.getCamera().update();*/
    }

    @Override
    public boolean keyDown(int keycode) {
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
        Link[] clickedLinks = new Link[3];

        //Debug:
        Gdx.app.log("Input", screenX+","+screenY);
        Gdx.app.log("Input", v.x+","+v.y);
        for(Node n:model.nodes.values())Gdx.app.log("Nodes", n.id+" "+n.x+" "+n.y);
        for(Link link:model.links)Gdx.app.log("Links", link.n1.id+" "+link.n2.id+" "+link.state);
        for(LinkSpace ln:model.linknodes)Gdx.app.log("Placeholder Nodes", ln.id+" "+ln.x+" "+ln.y);

        //lower the clickedLinks[x] number, the higher priority
        for(Link l : model.links){
            if(l.rect.overlaps(clickBox)){
                Gdx.app.log("Clicked", "y");
                if(l.state== Link.STATE.POTENTIAL){
                    clickedLinks[0]=l;
                }else if(l.selected){
                    clickedLinks[2]=l;
                }else{
                    clickedLinks[1]=l;
                }
            }
        }
        for(int i = 0; i < clickedLinks.length; i++){
            //clickedLinks is sorted by priority, lowest index is used and we return since we're done
            if(clickedLinks[i]!=null){
                model.selectLink(clickedLinks[i]);
                model.updateHighlight();
                if(model.nodes.get(model.nodes.size-1).on){
                    //Goal has been reached!
                    nextLevel();
                }
                levelinfo.setText("Level "+level+"  Moves: "+model.movesToComplete);
                return true;
            }
        }
        //Since we haven't returned from the for loop above, it means user clicked on nothing
        //If we just clicked away, clear the selected link
        model.clearSelection();
        model.updateHighlight();
        return true;
    }

    public void nextLevel(){
        ah.sendCustomTiming("User Interaction", (int) TimeUtils.timeSinceMillis(model.loadedMillis),
                "Time to finish a level", "Level "+level);

        ah.sendEvent("Level Balance",
                "Moves to finish a level", "Level "+level, model.movesToComplete);

        level++;
        loadLevel();

        sgh.write(new byte[]{(byte) level});
    }

    private void loadLevel(){
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
        //prefs.flush();
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

    }

}
