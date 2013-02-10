package com.untamedears.PrisonPearl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;

class FeedLog {
  private HashMap<String, String[]> feedLogHash;
	private ArrayList<String> flogEvents;
	private boolean initialised = false;
	
	public FeedLog() {
	}
	
	public String[] load(File file) {
		if(!initialised){
			try {
				loadFeedLog(file);
				initialised = true;
				String [] flogString = flogEvents.toArray(new String[flogEvents.size()]);
				return flogString;
			} catch (IOException e) {
				e.printStackTrace();
				Bukkit.getLogger().info("Failed to load file!");
				initialised = false;
				return new String[0];
			}
		}else{
			String [] flogString = flogEvents.toArray(new String[flogEvents.size()]);
			return flogString;
		}
	}
	
	private void loadFeedLog(File file) throws IOException {
		feedLogHash = new HashMap<String, String[]>();
		FileInputStream fis;
		fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		String line;
		flogEvents = new ArrayList<String>();
		while ((line = br.readLine()) != null) {
			if (line.length() > 1) {
				flogEvents.add(line);
			}
		}
		fis.close();
	}	
	
	private boolean saveLog(String log){
	
	return true;
	}
}
