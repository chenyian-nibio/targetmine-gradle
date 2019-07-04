package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

public class UMLSResolver {
	private File consoFile;
	private HashMap<String, String> nameIdMap = new HashMap<String, String>();
	
	public UMLSResolver(File consoFile) {
		this.consoFile = consoFile;
		try {
			loadData();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public void loadData() throws IOException {
		Files.lines(consoFile.toPath()).forEach(line ->{
			String[] split = line.split("\\|");
			String identifer = split[0];
			String name = split[14];
			nameIdMap.put(name.toLowerCase(), identifer);
		});
	}
	public String getIdentifier(String name) {
		return nameIdMap.get(name.toLowerCase());
	}
}
