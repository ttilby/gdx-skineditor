/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.shadebob.skineditor;

import java.io.IOException;

import org.shadebob.skineditor.screens.MainScreen;
import org.shadebob.skineditor.screens.WelcomeScreen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;


/**
 * Main game class I re-use everywhere in this program.
 * 
 * @author Yanick Bourbeau
 */
public class SkinEditorGame extends Game {
	
	
	public final static String[] widgets = { "Label","Button","TextButton", "ImageButton", "CheckBox", "TextField",  "List", "SelectBox", "ProgressBar", "Slider", "ScrollPane", "SplitPane", "Window", "Touchpad", "Tree" };

	public SpriteBatch batch;
	public Skin skin;
	public TextureAtlas atlas;

	public MainScreen screenMain;
	public WelcomeScreen screenWelcome;
	
	// Project related
	public Skin skinProject;
	
	// System fonts
	public SystemFonts fm;
	
	// Optional check
	public OptionalChecker opt;
	
	
	private static String projectsRawDir 	= ".skineditor_projects";
	private static String projectsPath 		= "d:/_test"; //absolute 
	
	public SkinEditorGame(String[] arg) {
//		projectsPath = null;
	}

	public static FileHandle getProjectsDirectory() {
		if (projectsPath !=null) {
			return Gdx.files.absolute(projectsPath).child(projectsRawDir);
		} else {
			return Gdx.files.local(SkinEditorGame.projectsRawDir);
		}
	}
	
	public static FileHandle getWorkingDirectory(){
		try {
			String current = new java.io.File( "." ).getCanonicalPath();
			FileHandle resourceDir = new FileHandle(current);//.parent().child("assets").child("resources");
			return resourceDir; 
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return Gdx.files.local("/");
	}

	@Override
	public void create() {
		
		opt = new OptionalChecker();
		
		fm = new SystemFonts();
		fm.refreshFonts();
		
		// Create projects folder if not already here
		
		FileHandle dirProjects = getProjectsDirectory();
		System.out.println("project dir is" + dirProjects.path());
		
		if (dirProjects.isDirectory() == false) {
			dirProjects.mkdirs();
		}
			String current = getWorkingDirectory().path();
			System.out.println("Rebuild from raw resources, kind of overkill, might disable it for production");
			System.out.println("internal path is:" +Gdx.files.internal("/resources"));
			System.out.println("working dir = "+current);
			
			FileHandle resourceDir = new FileHandle(current).parent().child("assets").child("resources");
			System.out.println("resourceDir  = "+resourceDir);
			
			
			TexturePacker.Settings settings = new TexturePacker.Settings();
			settings.combineSubdirectories = true;
			String input = resourceDir.child("raw").path();
			TexturePacker.process(settings, input, resourceDir.child("uiskin-editor").path(),"uiskin-editor");//"resources/uiskin");

			batch = new SpriteBatch();
			skin = new Skin();
			atlas = new TextureAtlas(resourceDir.child("uiskin.atlas"));
			
			 
			skin.addRegions(new TextureAtlas(resourceDir.child("uiskin.atlas")));
			skin.load(resourceDir.child("uiskin.json"));
			
			screenMain = new MainScreen(this);
			screenWelcome = new WelcomeScreen(this);
			setScreen(screenWelcome);
			
		 
		
	}
	

	
	/**
	 * Display a dialog with a notice
	 */
	public void showNotice(String title, String message, Stage stage) {
		Dialog dlg = new Dialog(title, skin);
		dlg.pad(20);
		dlg.getContentTable().add(message).pad(20);
		dlg.button("OK", true);
		dlg.key(com.badlogic.gdx.Input.Keys.ENTER, true);
		dlg.key(com.badlogic.gdx.Input.Keys.ESCAPE, false);
		dlg.show(stage);
	}
	
}
