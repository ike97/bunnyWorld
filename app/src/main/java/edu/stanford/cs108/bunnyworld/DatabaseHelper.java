package edu.stanford.cs108.bunnyworld;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static android.content.ContentValues.TAG;

public class DatabaseHelper implements BunnyWorldConstants {

    //iVars
/**********************************************/
    //Variable for single instance of DBSingleton
    private static DatabaseHelper single_instance = null;
    public static SQLiteDatabase db;
    private static Context mContext;
    private static boolean deleteDatabase = false;
    private static ArrayList<String> resourceNames = new ArrayList<String>();
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
/**********************************************/

    /**
     * Private constructor. Sets up database if necessary
     */
    public DatabaseHelper(Context context) {
        db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        Cursor cursor = db.rawQuery("SELECT * FROM sqlite_master WHERE type ='table' AND name = 'games';", null);
        if (cursor.getCount() == 0) {
            initializeDB();
        } else {
            if (resourceNames.isEmpty()) {
                String cmd = "SELECT * FROM resources WHERE resType = " + IMAGE + ";";
                Cursor nameCursor = db.rawQuery(cmd, null);
                while(nameCursor.moveToNext()){
                    String name = nameCursor.getString(0);
                    resourceNames.add(name);
                }
                nameCursor.close();
            }
        }
        cursor.close();
    }

    /**
     * Calls method to refresh the database from any activity
     * Refreshes database
     */
    public void refreshDatabase(Context context){
        String cmd = "DROP TABLE IF EXISTS games;";
        String cmd1 = "DROP TABLE IF EXISTS pages;";
        String cmd2 = "DROP TABLE IF EXISTS resources;";
        String cmd3 = "DROP TABLE IF EXISTS shapes;";
        db.execSQL(cmd);
        db.execSQL(cmd1);
        db.execSQL(cmd2);
        db.execSQL(cmd3);
        single_instance = new DatabaseHelper(context.getApplicationContext());
    }

    /**
     * Returns the single instance of the DatabaseHelper
     * @param context context of the caller
     */
    public static DatabaseHelper getInstance(Context context) {
        mContext = context;
        if (deleteDatabase) context.deleteDatabase(DATABASE_NAME);
        if (single_instance == null) {
            single_instance = new DatabaseHelper(context.getApplicationContext());
        }
        return single_instance;
    }

    /**
     * Adds a new game name to games table.
     * @param gameName string name of the game
     */
    public void addGameToTable(String gameName) {
        ContentValues cv = new ContentValues();
        cv.put("name", gameName);
        db.insert(GAMES_TABLE, null, cv);
//        String cmd = "INSERT INTO games VALUES ('" + gameName + "', NULL);";
//        db.execSQL(cmd);
    }

    /**
     * Given a table name and an entry name, returns boolean regarding whether an entry
     * with given name exists in given table.
     * @return returns true if there exists entryName in tableName.
     */
    public boolean entryExists(String tableName, String entryName) {
        Cursor cursor = db.rawQuery("SELECT * FROM " + tableName + " WHERE name = '" + entryName + "';", null);
        if ((cursor != null) && (cursor.getCount() != 0)) {
            cursor.close();
            return true;
        }
        if (cursor != null) {
            cursor.close();
        }
        return false;
    }

    /**
     * Given a table name and an entry name, returns boolean regarding whether an entry
     * with given name exists in given table.
     * @param tableName Name of table to search
     * @param entryName Name of entry to search for
     * @param parent_id Include parent_id to narrow search
     * @return returns true if there exists entryName in tableName.
     */
    public boolean entryExists(String tableName, String entryName, int parent_id) {
        Cursor cursor = db.rawQuery("SELECT * FROM " + tableName + " WHERE name = '" + entryName + "' AND parent_id = " + parent_id + ";", null);
        if ((cursor != null) && (cursor.getCount() != 0)) {
            cursor.close();
            return true;
        }
        if (cursor != null) {
            cursor.close();
        }
        return false;
    }

    /**
     * When there is no gameName passed into gameExists, returns whether there
     * are any games at all in the games table.
     * @return returns true if there exists at least one game. False otherwise.
     */
    public boolean gameExists() {
        Cursor cursor = db.rawQuery("SELECT * FROM games;", null);
        if ((cursor != null) && (cursor.getCount() != 0)) {
            cursor.close();
            return true;
        }
        if (cursor != null) {
            cursor.close();
        }
        return false;
    }

    /**
     * Returns the id for a given game.
     * @param tableName String name of the game.
     * @param entryName of the desired entry
     * @param parent_id Must be used if getting id of page or shape. Enter -1 otherwise.
     * @return id of corresponding entry. Returns -1 if entry does not exist.
     */
    public int getId(String tableName, String entryName, int parent_id) {
        if(!entryExists(tableName, entryName)) return -1;
        String cmd = "SELECT * FROM " + tableName + " WHERE name = '" + entryName + "'";
        if (parent_id != NO_PARENT) {
            cmd += "AND parent_id = " + parent_id;
        }
        cmd += ";";
        Cursor cursor = db.rawQuery(cmd, null);
        if(cursor.getCount() == 0) return -1; //in the case where we create a new page
        cursor.moveToFirst();
        int colIndex = cursor.getColumnIndex("_id");
        int id = cursor.getInt(colIndex);
        cursor.close();
        return id;
    }

    /**
     * Creates empty games, pages, shapes TABLES. Populates resources TABLE with
     * images and audio resources in res library.
     */
    public void initializeDB() {
        String cmd = "CREATE TABLE games (name Text, _id INTEGER PRIMARY KEY AUTOINCREMENT);"; //Create games table
        db.execSQL(cmd);
        cmd = "CREATE TABLE pages (name Text, parent_id INTEGER, rendering BLOB NOT NULL, _id INTEGER PRIMARY KEY AUTOINCREMENT);"; //Create pages table
        db.execSQL(cmd);
        cmd = "CREATE TABLE shapes (name Text, parent_id INTEGER, res_id INTEGER, x REAL, y REAL, width REAL, height REAL, msg Text, scripts Text, movable BOOLEAN, visible BOOLEAN, _id INTEGER PRIMARY KEY AUTOINCREMENT);"; //Create shapes table
        db.execSQL(cmd);
        cmd = "CREATE TABLE resources (name Text, resType INTEGER, file BLOB, _id INTEGER PRIMARY KEY AUTOINCREMENT);";
        db.execSQL(cmd);
        addAudioResources();
        addImageResources();
    }

    /**
     * Adds all drawable image resources into the database.
     */
    private void addImageResources() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        //get the first 6 items into the resource folder
        int count = 1;
        resourceNames = new ArrayList<String>();
        for (int curr : imgList) {
            String resourceName = mContext.getResources().getResourceEntryName(curr);
            if(count < 7){ resourceNames.add(resourceName); count += 1; }
            Bitmap bitmap = ((BitmapDrawable) Objects.requireNonNull(mContext.getDrawable(curr))).getBitmap();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] bitmapdata = stream.toByteArray();
            ContentValues cv = new ContentValues();
            cv.put("name", resourceName);
            cv.put("resType", IMAGE);
            cv.put("file", bitmapdata);
            db.insert("resources", null, cv);
            stream.reset();
        }

        try {
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds all audio file resources into database
     */
    private void addAudioResources() {
        InputStream inputStream = null;
        FileInputStream fin = null;

        for (int curr : audioList) {
            String name = mContext.getResources().getResourceEntryName(curr);
            inputStream = mContext.getResources().openRawResource(curr);
            File tempFile = null;
            try {
                //Read mp3 resource as a file
                tempFile = File.createTempFile("name", "mp3");
                tempFile.deleteOnExit();
                Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                //Store audio data file as byte[]
                byte[] audioBytes = new byte[(int)tempFile.length()];
                fin = new FileInputStream(tempFile);
                fin.read(audioBytes);

                //Put audio resource into db
                ContentValues cv = new ContentValues();
                cv.put("name", name);
                cv.put("resType", AUDIO);
                cv.put("file", audioBytes);
                db.insert("resources", null, cv);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            if (inputStream != null) inputStream.close();
            if (fin != null) fin.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param res_id Resource id of the wanted image
     * @return Bitmap of image res_id in shapes
     */
    public Bitmap getImage(int res_id) {
        String cmd = "SELECT * FROM resources WHERE _id =" + res_id + ";";
        Cursor cur = db.rawQuery(cmd, null);

        if (cur.moveToFirst()) {
            byte[] imgByte = cur.getBlob(FILE_COL_INDEX);
            cur.close();
            //return BitmapFactory.decodeByteArray(imgByte, 0, imgByte.length);
            Bitmap newBitmap = BitmapFactory.decodeByteArray(imgByte, 0, imgByte.length);
            Bitmap newer = Bitmap.createScaledBitmap(newBitmap, 400, 300, false);
            return newer;
        }
        if (cur != null && !cur.isClosed()) cur.close();
        return null;
    }

    /**
     *
     * @param name name of the image stored
     * @return uses the name of the file to return its associated bitmap
     */
    public Bitmap getImage(String name){
        String cmd = "SELECT * FROM resources WHERE name = '" + name + "';";
        Cursor cur = db.rawQuery(cmd, null);

        if (cur.moveToFirst()) {
            byte[] imgByte = cur.getBlob(FILE_COL_INDEX);
            cur.close();
            Log.d("tag1", name);
            Bitmap newBitmap = BitmapFactory.decodeByteArray(imgByte, 0, imgByte.length);
            Bitmap newer = null;
            if(newBitmap != null) newer = Bitmap.createScaledBitmap(newBitmap, 500, 500, false);
            return newer;
        }
        if (cur != null && !cur.isClosed()) cur.close();
        return null;
    }

    /**
     * Method that simply returns the resource names for the spinners
     * @return the arraylist of the resource names
     */
    public ArrayList<String> getResourceNames(){
        resourceNames.clear();
        String cmd = "SELECT * FROM resources WHERE resType = " + IMAGE + ";";
        Cursor nameCursor = db.rawQuery(cmd, null);
        int counter = 0;
        while(nameCursor.moveToNext() && counter < 7){
            Log.d("counter", Integer.toString(counter));
            counter++;
            String name = nameCursor.getString(0);
            resourceNames.add(name);
        }
        nameCursor.close();
        return resourceNames;
    }

    /**
     * @param id id of the resource
     * returns the resource name of that resource ID
     */
    public String getResourceName(int id){
        String cmd = "SELECT * FROM "+ RESOURCE_TABLE +" WHERE _id = "+ id +";";
        Cursor cursor = db.rawQuery(cmd, null);
        cursor.moveToFirst();
        String name = cursor.getString(0);
        return name;
    }

    /**
     * This method will take in a resource id and attempt to convert it into an mp3.
     * @param res_id The resource id for the audio file you are seeking. Ensure that this
     *               is indeed an audio -- not image -- file.
     * @return A file containing the bytecode for your mp3 or null if no such resource was
     * found.
     */
    public File getAudioFile(int res_id) {
        String cmd = "SELECT * FROM resources WHERE _id =" + res_id + ";";
        Cursor cur = db.rawQuery(cmd, null);
        File soundDataFile = null;

        if (cur.moveToFirst()) {
            String resourceName = cur.getString(NAME_COL);
            try {
                soundDataFile = File.createTempFile(resourceName, "mp3");
                byte[] soundBytes = cur.getBlob(FILE_COL_INDEX);
                FileOutputStream fos = new FileOutputStream(soundDataFile);
                fos.write(soundBytes);

                cur.close();
                fos.close();
                return soundDataFile;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (cur != null && !cur.isClosed()) cur.close();
        return null;
    }

    /**
     * Adds a resource to the database.
     * @param resourceName Desired name for the resource
     * @param dataType Integer representing datatype. AUDIO = 0 / IMAGE = 1
     * @param byteData Resource data as a byte[]
     * @return Returns true if successfully added to the database.
     */
    public boolean addResource(String resourceName, int dataType, byte[] byteData) {
        if (entryExists(RESOURCE_TABLE, resourceName)) { //Checks if resource with name exists already
            Toast.makeText(mContext, "Resource with name '" + resourceName + "' already exists.", Toast.LENGTH_SHORT).show();
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put("name", resourceName);
        cv.put("resType", dataType);
        cv.put("file", byteData);
        db.insert(RESOURCE_TABLE, null, cv);
        if(dataType == IMAGE) resourceNames.add(resourceName);
        return true;
    }

    /**
     * Changes the name of an entry within the database. Not to be used
     * @param tableName The name where entry to be changed is contained
     * @param _id The id of the entry that is to be changed
     * @param newName The new desired name of the entry
     * @return Returns true if name is successfully changed. Returns false if there is no corresponding
     *         entry to change.
     */
    public boolean changeEntryName(String tableName, int _id, String newName) {
        String cmd = "UPDATE " + tableName + " SET name = '" + newName + "' WHERE _id = '" + _id + "';";
        db.execSQL(cmd);
        return true;
    }

    /**
     * Adds a shape to the shapes table in the database.
     * @param name Shape name
     * @param parent_id Id of the page the shape belongs to
     * @param res_id Id of the resource the shape uses
     * @param x X coordinate
     * @param y Y coordinate
     * @param width Width of shape
     * @param height Height of shape
     * @param msg Message that the shape displays
     * @param scripts Scripts for shape
     * @param moveable Boolean representing whether shape is moveable on page
     * @param visible Boolean representing whether shape is visible on page
     * @return Returns true if shape is successfully added to shapes table.
     */
    public boolean addShape(String name, int parent_id, int res_id, double x, double y, double width,
                            double height, String msg, String scripts, boolean moveable, boolean visible) {

        if (entryExists(SHAPES_TABLE, name, parent_id)) {
            Toast.makeText(mContext, "Shape with name '" + name + "' already exists.", Toast.LENGTH_SHORT).show();
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("parent_id", parent_id);
        cv.put("res_id", res_id);
        cv.put("x", x);
        cv.put("y", y);
        cv.put("width", width);
        cv.put("height", height);
        cv.put("msg", msg);
        cv.put("scripts", scripts);
        cv.put("movable", moveable);
        cv.put("visible", visible);
        db.insert(SHAPES_TABLE, null, cv);
        return true;
    }

    /**
     * Removes a given entry in a given table from database.
     * @param table Name of table where desired entry is located
     * @param entry_id Unique db id for desired table entry
     * @return Returns true if the number of entries deleted > 0
     */
    public boolean removeEntry(String table, int entry_id) {
        return (db.delete(table, "_id=?", new String[]{Integer.toString(entry_id)}) > 0);
    }

    /**
     * Changes the resource that the shape accesses in the database.
     * @param shape_id id of the shape to be changed
     * @param res_id id of the new image resource to be added to shape
     */
    public void changeShapeImage(int shape_id, int res_id) {
        String cmd = "UPDATE " + SHAPES_TABLE + " SET res_id = " + res_id + " WHERE _id = " + shape_id + ";";
        db.execSQL(cmd);
    }

    /**
     * Updates the location/dimensions of the shape in the database.
     * @param shape_id Id of the shape to be changed
     * @param x X coordinate value. Use NO_CHANGE_X if value is to stay constant.
     * @param y Y coordinate value. Use NO_CHANGE_Y if value is to stay constant.
     * @param width Width of shape. Use NO_CHANGE_WIDTH if value is to stay constant.
     * @param height Height of shape. Use NO_CHANGE_HEIGHT if value is to stay constant.
     */
    public void changeShapeDimensions(int shape_id, double x, double y, double width, double height) {
        boolean commaNeeded = false;
        String myCmd = "UPDATE " + SHAPES_TABLE + " SET ";
        if (x != NO_CHANGE_X) {
            myCmd += "x =" + x;
            commaNeeded = true;
        }
        if (y != NO_CHANGE_Y) {
            if (commaNeeded) myCmd += ", ";
            myCmd += "y = " + y;
            commaNeeded = true;
        }
        if (width != NO_CHANGE_WIDTH) {
            if (commaNeeded) myCmd += ", ";
            myCmd += "width = " + width;
            commaNeeded = true;
        }
        if (height != NO_CHANGE_HEIGHT) {
            if (commaNeeded) myCmd += ", ";
            myCmd += "height = " + height;
        }


        myCmd += " WHERE shape_id = " + shape_id + ";";
        db.execSQL(myCmd);
    }

    /**
     * Updates the message that the shape will display to a user-inputted string in the database
     * @param shape_id Unique id of the shape in the database.
     * @param msg Message the shape is to display.
     */
    public void changeShapeMsg(int shape_id, String msg) {
        String cmd = "UPDATE " + SHAPES_TABLE + " SET msg = '" + msg + "' WHERE _id = " + shape_id + ";";
        db.execSQL(cmd);
    }
    /**
     * Replace the script for a specified shape in database.
     * @param shape_id id for the applicable shape
     * @param scripts ArrayList<String> containing list of wanted scripts for the specified shape
     */
    public void changeShapeScript(int shape_id, ArrayList<String> scripts) {
        String cmd = "UPDATE " + SHAPES_TABLE + " SET scripts = '" + scripts.toString() + "' WHERE _id = " + shape_id + ";";
        db.execSQL(cmd);
    }

    /**
     * Set the visibility boolean for a specified shape in the database.
     * @param shape_id unique db id for the shape
     * @param visible boolean describing shape visibility condition
     */
    public void setShapeVisibility(int shape_id, boolean visible) {
        String cmd = "UPDATE " + SHAPES_TABLE + " SET visible = " + visible + " WHERE _id = " + shape_id + ";";
        db.execSQL(cmd);
    }

    /**
     * Sets the moveable boolean for a specified shape in the database.
     * @param shape_id unique db id for the shape
     * @param moveable boolean describing shape moveable condition
     */
    public void setShapeMovability(int shape_id, boolean moveable) {
        String cmd = "UPDATE " + SHAPES_TABLE + " SET moveable = " + moveable + " WHERE _id = " + shape_id + ";";
        db.execSQL(cmd);
    }

    /**
     * Returns a ImageShape from database corresponding with its id.
     * @param shape_id The id of the shape you want to retrieve
     * @param view The view in which you want the shape to appear
     * @return TextShape object
     */
    public Shape getShape(int shape_id, View view) {
        String getShapeRow = "SELECT * FROM " + SHAPES_TABLE + " WHERE _id = " + shape_id + ";";
        Cursor cursor = db.rawQuery(getShapeRow, null);
        cursor.moveToFirst();

        String name = cursor.getString(NAME_COL);
        int res_id = cursor.getInt(2);
        float x = (float)cursor.getDouble(3);
        float y = (float)cursor.getDouble(4);
        float width = (float)cursor.getDouble(5);
        float height = (float)cursor.getDouble(6);
        String txtString = cursor.getString(7);
        String script = cursor.getString(8);
        boolean moveable = cursor.getInt(9) > 0;
        boolean visible = cursor.getInt(10) > 0;

        RectF bounds = new RectF(x, y, x + width, y + height);

        //in the case where res_id == -1, return null object for bitmap
        Bitmap newBitmap = null; BitmapDrawable drawable = null;
        if(res_id != -1){
            newBitmap = getImage(res_id);
            drawable = new BitmapDrawable(newBitmap);
        }

        //get and return proper shape based on parameters
        Shape shape;
        if(txtString.isEmpty() && res_id == -1) //create rectShape
            shape = new RectangleShape(view, bounds, -1,visible,moveable,name);
        else if(!txtString.isEmpty()) //create textShape
            shape = new TextShape(view, bounds, drawable,txtString,-1,visible,moveable,name);
        else
            shape = new ImageShape(view, bounds, drawable, txtString, res_id, visible, moveable, name);


        //finally set the script and return the shape
        shape.setScript(Script.parseScript(script));
        Log.d("ScriptVal", "Script at database retrieval: " + script);
        cursor.close();

        return shape;
    }

    /**
     * Return a list of shapes within a page.
     * @param parent_id The id of the page where images are stored
     * @param view Pass in the view of the caller
     * @return Return an arraylist of all shapes within a page
     */
    public ArrayList<Shape> getPageShapes(int parent_id, View view) {
        ArrayList<Shape> pageShapes = new ArrayList<>();
        String cmd = "SELECT * FROM " + SHAPES_TABLE + " WHERE parent_id = " + parent_id + ";";
        Cursor cursor = db.rawQuery(cmd, null);
        while (cursor.moveToNext()) {
            int shape_id = cursor.getInt(11);
            pageShapes.add(getShape(shape_id, view));
        }
        cursor.close();
        return pageShapes;
    }

    /**
     * Inserts a new page into pages database.
     * @param pageName Desired name of page.
     * @param rendering Bitmap of page rendering. Pass NULL if none.
     * @param parent_id Id of game that page belongs to.
     * @return Returns true if page is successfully added to database.
     */
    public boolean addPage(String pageName, Bitmap rendering, int parent_id) {
        if (entryExists(PAGES_TABLE, pageName, parent_id)) {
            Toast.makeText(mContext, "Page with name '" + pageName +"' already exists.", Toast.LENGTH_SHORT).show();
            return false;
        }
        byte[] bitmapdata = null;
        if(rendering != null){
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            rendering.compress(Bitmap.CompressFormat.PNG, 100, stream);
            bitmapdata = stream.toByteArray();
        }
        ContentValues cv = new ContentValues();
        cv.put("name", pageName);
        cv.put("rendering", bitmapdata);
        cv.put("parent_id", parent_id);
        db.insert(PAGES_TABLE, null, cv);
        return true;
    }

    /**
     * Removes a page and all of its associated shapes from the database
     * @param page_id The unique id where the page is stored in the database
     */
    public void deletePage(int page_id) {
        String pageDelete = "DELETE FROM " + PAGES_TABLE + " WHERE _id = " + page_id + ";";
        db.execSQL(pageDelete);
        String pageShapeDelete = "DELETE FROM " + SHAPES_TABLE + " WHERE parent_id = " + page_id + ";";
        db.execSQL(pageShapeDelete);
    }

    public ArrayList<Shape> getGameShapes(int game_id, View view) {
        ArrayList<Shape> gameShapes = new ArrayList<>();
        for (String currPageName : getGamePageNames(game_id)) {
            int currId = getId(PAGES_TABLE, currPageName, game_id);
            for (Shape currShape : getPageShapes(currId, view)) {
                gameShapes.add(currShape);
            }
        }
        return gameShapes;
    }

    /**
     * Change a given page's name in the database
     * @param page_id Unique id where page is stored in the database
     * @param newName Desired new name for the page
     */
    public void changePageName(int page_id, String newName) {
        String cmd = "UPDATE " + PAGES_TABLE + " SET name = '" + newName + "' WHERE _id = " + page_id + ";";
        db.execSQL(cmd);
    }

    /**
     * Updates a given page's rendering thumbnail with specified bitmap.
     * @param page_id Table ID of the specified page.
     * @param rendering Bitmap of desired img resolution.
     */
    public void changePageThumbnail(int page_id, Bitmap rendering) {
        if (rendering == null) {
            String cmd = "UPDATE " + PAGES_TABLE + " SET rendering = " + -1 + " WHERE _id = " + page_id + ";";
            db.execSQL(cmd);
            return;
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        rendering.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] bitmapdata = stream.toByteArray();
        ContentValues cv = new ContentValues();
        cv.put("rendering", bitmapdata);
        db.update(PAGES_TABLE, cv, "_id=?", new String[]{Integer.toString(page_id)});
    }

    /**
     * @param pageId the id of the page
     * @return the bitmap rendering for that image
     */
    public Bitmap getPageRendering(int pageId){
        String cmd = "SELECT * FROM pages WHERE _id =" + pageId + ";";
        Cursor cur = db.rawQuery(cmd, null);
        if (cur.moveToFirst()) {
            byte[] imgByte = cur.getBlob(FILE_COL_INDEX);
            cur.close();
            //return BitmapFactory.decodeByteArray(imgByte, 0, imgByte.length);
            Bitmap newBitmap = BitmapFactory.decodeByteArray(imgByte, 0, imgByte.length);
            Bitmap newer = Bitmap.createScaledBitmap(newBitmap, 300, 300, false);
            return newer;
        }
        if (cur != null && !cur.isClosed()) cur.close();
        return null;
    }

    //for optimization:
    //returns true if the game had pages else false
    public void deleteGame(String gameName) {
        String cmd = "SELECT * FROM games WHERE name = '" + gameName + "';";
        Cursor cursor = db.rawQuery(cmd, null);
        if(cursor.getCount() != 0)  {
            cursor.moveToFirst();
            int game_id = cursor.getInt(1);
            String cmd1 = "SELECT * FROM pages WHERE parent_id = " + game_id + ";";
            Cursor cursor1 = db.rawQuery(cmd1, null);
            //loop through each page and delete corresponding shapes
            if(cursor1.getCount() != 0){
                cursor1.moveToFirst();
                while(cursor1.moveToNext()){
                    int pageId = cursor1.getInt(3);
                    //delete all shapes that have the page Id
                    String cmd2 = "DELETE FROM shapes WHERE parent_id = " + pageId + ";";
                    db.execSQL(cmd2);
                }
                //then delete the page itself
                String cmd3 = "DELETE FROM pages WHERE parent_id = " + game_id + ";";
                db.execSQL(cmd3);
                cursor1.close();
            }
        }
        if(cursor.getCount() != 0) db.execSQL("DELETE FROM games WHERE name = '" + gameName +"';");
        cursor.close();
    }

    /**
     * returns the most recent count number for the pages in that game
     * @param id id of the game
     * @param tableName name of the table
     * @return
     */
    public int getLatestCount(String tableName, int id){
        if(id == -1) return 0; //in the case where a new game is created or a new page is created
        String cmd = "SELECT * FROM " + tableName + " WHERE parent_id = "+ id +";";
        Cursor cursor = db.rawQuery(cmd, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    /**
     * Returns an ArrayList of page names for a specified game
     * @param gameId The id of the given game as stored in the games table in the database
     * @return Arraylist of page names within specified game
     */
    public ArrayList<String> getGamePageNames(int gameId) {
        ArrayList<String> pageNames = new ArrayList<>();
        String cmd = "SELECT * FROM " + PAGES_TABLE + " WHERE parent_id =" + gameId +";";
        Cursor cursor = db.rawQuery(cmd, null);
        while (cursor.moveToNext()) {
            String pageName = cursor.getString(NAME_COL);
            pageNames.add(pageName);
        }
        cursor.close();
        return pageNames;
    }

    /**
     *It only works for pages and shapes
     * @param tableName the name of the table of that object
     * @param name name of the object itself
     * @return the id of it's parent if it has one else it returns -1
     */
    public int getParentId(String tableName, String name){
        if(tableName.equals(GAMES_TABLE) || tableName.equals(RESOURCE_TABLE)) return -1;
        String cmd = "SELECT * FROM "+ tableName + " WHERE name = '"+ name +"';";
        Cursor cursor = db.rawQuery(cmd, null);
        int id = cursor.getInt(1);
        cursor.close();
        return id;
    }

    public static File backupDatabase(Activity activity) throws IOException {
        //Open your local db as the input stream
        String inFileName = db.getPath();
        File dbFile = new File(inFileName);
        FileInputStream fis = new FileInputStream(dbFile);

        String outFileName = Environment.getExternalStorageDirectory() + "/" + DATABASE_NAME + ".db";
        //Open the empty db as the output stream
        verifyStoragePermissions(activity);
        File backup = new File(outFileName);
        if (!backup.exists()) backup.createNewFile();
        OutputStream output = new FileOutputStream(outFileName);
        //transfer bytes from the inputfile to the outputfile
        byte[] buffer = new byte[1024];
        int length;
        while ((length = fis.read(buffer))>0){
            output.write(buffer, 0, length);
        }
        //Close the streams
        output.flush();
        output.close();
        fis.close();
        return backup;
    }


    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public void replaceDatabase(Uri newDbUri) throws IOException {
        String toPath = mContext.getDatabasePath(DATABASE_NAME).getParent();
        mContext.deleteDatabase(DATABASE_NAME);
//        if (Environment.getExternalStorageState() != null) {
//            File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/MyAppFolder");
//            String fromPath = dir.getAbsolutePath() + DATABASE_NAME;
            try {
                fileCopy(new File(toPath), newDbUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
       // }
    }

    private void fileCopy(File dst, Uri newDbUri) throws IOException {
        FileInputStream in = (FileInputStream) mContext.getContentResolver().openInputStream(newDbUri);
        if (dst.isDirectory()) dst = new File(dst.getPath() + File.separator + DATABASE_NAME);
        OutputStream out = new FileOutputStream(dst);

        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.flush();
        in.close();
        out.close();
        db = SQLiteDatabase.openOrCreateDatabase(dst, null);
    }

}