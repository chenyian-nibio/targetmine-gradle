package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class UMLSResolver {
	private HashMap<String, String> nameIdMap = new HashMap<String, String>();

	public UMLSResolver(File consoFile,File styFile) throws IOException {
		loadData(consoFile,styFile);
	}

	public void loadData(File consoFile,File styFile) throws IOException {
		try(UMLSParser parser = new UMLSParser(consoFile, styFile,UMLSParser.DATA_TYPES)){
			UMLS umls = null;
			while((umls = parser.getNext())!=null) {
				nameIdMap.put(umls.getName().toLowerCase(), umls.getIdentifier());
			}
		}
	}
	public String getIdentifier(String name) {
		return nameIdMap.get(name.toLowerCase());
	}
}
