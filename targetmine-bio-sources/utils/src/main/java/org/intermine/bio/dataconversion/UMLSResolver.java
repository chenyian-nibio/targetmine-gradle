package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

public class UMLSResolver {
	private UMLSParser parser;
	private HashMap<String, String> nameIdMap = new HashMap<String, String>();

	public UMLSResolver(File consoFile,File styFile) throws FileNotFoundException {
		this.parser = new UMLSParser(consoFile, styFile);
		parser.setSemanticTypes(UMLSParser.DATA_TYPES);
		try {
			loadData(consoFile,styFile);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void loadData(File consoFile,File styFile) throws IOException {
		try(UMLSParser parser = new UMLSParser(consoFile, styFile)){
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
