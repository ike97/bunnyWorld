package edu.stanford.cs108.bunnyworld;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE;

public class GameLoaderActivity extends AppCompatActivity implements BunnyWorldConstants {
    //iVars
    private DatabaseHelper dbHelper;
    private String[] fromArray = {"name"};
    private int[] toArray = {android.R.id.text1};
    public static boolean playing = false;
    private static final int CHOOSE_FILE_REQUESTCODE = 8777;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_game);
        dbHelper = DatabaseHelper.getInstance(this); //Get singleton instance of DBHelper class

        //Enters full screen mode
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.hide();
        getWindow().getDecorView().setSystemUiVisibility(SYSTEM_UI_FLAG_IMMERSIVE |
                SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        setupSpinner();

        playerOrChooserSetup();
    }

    public void playerOrChooserSetup() {
        playing = getIntent().getBooleanExtra("playing", false);
        if (playing) {
            Button createGameBtn = findViewById(R.id.createGameBtn);
            EditText newGameNameEditor = findViewById(R.id.newGameNameEditor);
            TextView createNewGameText = findViewById(R.id.createNewGameText);
            TextView editExistingGameText = findViewById(R.id.editExistingGameText);
            createGameBtn.setVisibility(View.GONE);
            newGameNameEditor.setVisibility(View.GONE);
            createNewGameText.setVisibility(View.GONE);
            editExistingGameText.setText("Play Game");
        }
    }

    protected void onResume() {
        super.onResume();
        setupSpinner();
    }

    //Populates spinner with database game names
    private void setupSpinner() {
        Spinner spinner = (Spinner) findViewById(R.id.existingGamesSpinner);
        if (dbHelper.gameExists()) {
            Cursor cursor = dbHelper.db.rawQuery("SELECT * FROM games;", null);
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_spinner_item, cursor, fromArray, toArray, 0);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        } else { //Populates spinner with 'no game files' msg if database is empty
            String[] arraySpinner = new String[]{};
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, arraySpinner);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }
    }

    //Adds a new game to the database according to what the user types edittext. Checks that gamename is not taken.
    public void createNewGame(View view) {
        EditText editText = (EditText) findViewById(R.id.newGameNameEditor);
        String gameName = editText.getText().toString();
        if (gameName.isEmpty()) {
            Toast.makeText(this, "No name entered.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dbHelper.entryExists(GAMES_TABLE, gameName)) {
            Toast.makeText(this, "A game with that name already exists. Use a different name.", Toast.LENGTH_LONG).show();
            return;
        }
        dbHelper.addGameToTable(gameName);
        editText.setText("");
        Intent intent = new Intent(this, PreviewPagesActivity.class);
        intent.putExtra("Game_id", dbHelper.getId(GAMES_TABLE, gameName, NO_PARENT));
        startActivity(intent);
    }

    public void openGameFile(View view) {
        Spinner spinner = (Spinner) findViewById(R.id.existingGamesSpinner);
        Cursor gameCursor = (Cursor) spinner.getSelectedItem();
        if (gameCursor == null) {
            Toast.makeText(this, "No games in database", Toast.LENGTH_LONG).show();
            return;
        }
        String gameName = gameCursor.getString(0);
        Intent intent;
        if (playing) {
            intent = new Intent(this, PlayGameActivity.class);
            //intent.putExtra();
        } else {
            intent = new Intent(this, PreviewPagesActivity.class);
        }
        intent.putExtra("Game_id", dbHelper.getId(GAMES_TABLE, gameName, NO_PARENT));
        startActivity(intent);
    }

    public void deleteGameFile(View view) {
        Spinner spinner = (Spinner) findViewById(R.id.existingGamesSpinner);
        Cursor gameCursor = (Cursor) spinner.getSelectedItem();
        if (gameCursor == null) {
            Toast.makeText(this, "No games in database", Toast.LENGTH_LONG).show();
            return;
        }
        String gameName = gameCursor.getString(0);
        //delete that from the database and repopulate the spinner
        dbHelper.deleteGame(gameName);

        //populate the spinner with the new game list
        String newCmd = "SELECT * FROM games;";
        Cursor cursor = dbHelper.db.rawQuery(newCmd, null);
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_spinner_item,
                cursor, fromArray, toArray, 0);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
//        int count = cursor.getCount();
//        if(count == 0){
//            dbHelper.refreshDatabase(this);
//            dbHelper = DatabaseHelper.getInstance(this);
//        }
    }

    public void exportDatabase(View view) {
        File exportFile = null;
        try {
            exportFile = DatabaseHelper.backupDatabase(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (exportFile == null) {
            Toast.makeText(this, "Unsuccessful export.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("*/.db");
        sharingIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://"+exportFile.getPath()));
        startActivity(Intent.createChooser(sharingIntent, "Share game files using"));
    }

    public void importDatabase(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, CHOOSE_FILE_REQUESTCODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {

        if (requestCode == CHOOSE_FILE_REQUESTCODE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri fileUri = resultIntent.getData();
                Cursor cur = this.getContentResolver().query(fileUri, null, null, null, null);
                cur.moveToFirst();
                int nameIndex = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                String name = cur.getString(nameIndex);
                int len = name.length();
                if (name.charAt(len -1) != 'b' || name.charAt(len - 2) != 'd' || name.charAt(len - 3) != '.') {
                    Toast.makeText(this, "Cannot load from chosen file.", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    dbHelper.replaceDatabase(fileUri);
                    this.finish();
                    startActivity(getIntent());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }//onActivityResult
    }
}
