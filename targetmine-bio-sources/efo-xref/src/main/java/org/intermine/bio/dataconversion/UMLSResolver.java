package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class UMLSResolver {
	private File consoFile;
	private File styFile;
	private HashMap<String, String> nameIdMap = new HashMap<String, String>();
   private static final String[] DATA_TYPES = new String[]{
        "B2.2.1.2",//Pathologic Function
        "B2.3",//Injuery or Poisoning
        "A2.2.2",//Sign or Symptom
        "A1.2.2"//Anatomical Abnormality
        };

	public UMLSResolver(File consoFile,File styFile) {
		this.consoFile = consoFile;
		this.styFile = styFile;
		try {
			loadData();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Set<String> loadStyFile() throws IOException {
		if(styFile==null) {
			return null;
		}
		HashSet<String> cuiSet = new HashSet<String>();
		Files.lines(styFile.toPath()).forEach(line ->{
			String[] split = line.split("\\|");
			String cui = split[0];
			String str = split[2];
			for(String type :DATA_TYPES){
             		   if(str.startsWith(type)) {
                      		 cuiSet.add(cui);
				break;
                	    }
                        }
		});
		return cuiSet;
	}
	public void loadData() throws IOException {
		Set<String> set = loadStyFile();
		Files.lines(consoFile.toPath()).forEach(line ->{
			String[] split = line.split("\\|");
			String identifer = split[0];
			if(set== null || set.contains(identifer)) {
				String name = split[14];
				nameIdMap.put(name.toLowerCase(), identifer);
			}
		});
	}
	public String getIdentifier(String name) {
		return nameIdMap.get(name.toLowerCase());
	}
}
