package com.roclas;

import java.io.FileOutputStream;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;


public class DriveOperator{
	private static String CLIENT_ID = "603315453691-pb5mkj6bgf6f8qqb02kgkrb458n0mkm2.apps.googleusercontent.com";
	private static String CLIENT_SECRET = "hsXchpE_XN52eSoQa1FfKG06";

	//private static String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"; // default's app redirect_uri
	private static GoogleAuthorizationCodeFlow flow = null;
	//private static String accessToken=null;
	
	private static int miniserver_port;
	private static String accessToken;
	private static String userEmail;
	private static Drive service=null;
	private static String REDIRECT_URI;
	private static Properties props;
	private static String folder_mime_type="application/vnd.google-apps.folder";

	public DriveOperator() {
		super();
		props = getProperties();
		String port = props.getProperty("miniserver_port");
		if(port!=null)miniserver_port=Integer.parseInt(port);
		else miniserver_port=8123;
		accessToken=props.getProperty("accessToken");
		userEmail=props.getProperty("userEmail");
		REDIRECT_URI="http://localhost:"+miniserver_port;
		//setProperties(props);
	}

	private static void restartService() throws IOException {
		accessToken = null;
		flow=null;
		service=null;
		service=getDriveService();
	}

	public static void openURI(String uri_str) {
		if (!java.awt.Desktop.isDesktopSupported()) {
			System.err.println("Desktop is not supported (fatal)");
			System.exit(1);
		}

		java.awt.Desktop desktop = java.awt.Desktop.getDesktop();

		if (!desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
			System.err.println("Desktop doesn't support the browse action (fatal)");
			System.exit(1);
		}
		try {
			java.net.URI uri = new java.net.URI(uri_str);
			desktop.browse(uri);
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}


	/**
	 * Build an authorization flow and store it as a static class attribute.
	 * 
	 * @return GoogleAuthorizationCodeFlow instance.
	 * @throws IOException
	 *             Unable to load client_secrets.json.
	 */
	static GoogleAuthorizationCodeFlow getFlow() throws IOException {
		if (flow == null) {
			HttpTransport httpTransport = new NetHttpTransport();
			JacksonFactory jsonFactory = new JacksonFactory();
			flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport,
					jsonFactory, CLIENT_ID, CLIENT_SECRET,
					Arrays.asList(DriveScopes.DRIVE)).setAccessType("online")
					.setApprovalPrompt("auto").build();
		}
		return flow;
	}

	public static String getAuthorizationUrl(String emailAddress, String state) throws IOException {
		GoogleAuthorizationCodeRequestUrl urlBuilder = getFlow().newAuthorizationUrl().setRedirectUri(REDIRECT_URI);
		if (state != null) urlBuilder.setState(state);
		urlBuilder.set("user_id", emailAddress);
		return urlBuilder.build();
	}

	/**
	 * Retrieved stored credentials for the provided user ID.
	 * 
	 * @param userId
	 *            User's ID.
	 * @return Stored Credential if found, {@code null} otherwise.
	 * @throws IOException
	 */
	static Credential getStoredCredentials(String userId) throws IOException {
		GoogleCredential credentials;
		if (accessToken != null) {
			credentials = new GoogleCredential.Builder()
					.setClientSecrets(CLIENT_ID, CLIENT_SECRET).build()
					.setAccessToken(accessToken);
		} else {
			credentials = authorizeViaWeb(userId);
		}
		return credentials;
	}

	static GoogleCredential authorizeViaWeb(String userId) throws IOException {
		GoogleCredential credential = new GoogleCredential();
		String url = getAuthorizationUrl(userId, null);
		/*
		System.out.println("Please open the following URL in your browser then type the authorization code:");
		System.out.println("  " + url);
		openURI(url);
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String code = br.readLine();
		*/
		openURI(url);
		MiniServer miniServer=new MiniServer();
		String code=miniServer.start_and_get_code(miniserver_port);
		GoogleTokenResponse response = getFlow().newTokenRequest(code).setRedirectUri(REDIRECT_URI).execute();
		credential.setFromTokenResponse(response);
		accessToken = credential.getAccessToken();
		props.setProperty("accessToken", accessToken);
		saveProperties(props);
		
		System.out.println("Access Token: "+accessToken);
		return credential;
	}

	  private static  HashMap<String,File> retrieveAllDirs(Drive service)throws IOException {
	    ArrayList<File> result2 = new ArrayList<File>();
	    HashMap<String,File> result = new HashMap<String,File>();
	    Files.List request = service.files().list();
	    do {
	      try {
	        FileList files = request.execute();
	        result2.addAll(files.getItems());
	        request.setPageToken(files.getNextPageToken());
	      } catch (IOException e) {
	        String errormessage="IOException: " + e;
	        if(errormessage.toLowerCase().contains("invalid credentials")){
	        	return null;
	        }else{
	        	System.out.println(errormessage);
	        }
	      }
	    } while (request.getPageToken() != null && request.getPageToken().length() > 0);
	    for(File file:result2){
			String type=file.getMimeType();
			if(type.toLowerCase().trim().equals(folder_mime_type))result.put(file.getId(),file);
	    }
	    return result;
	  }
	  
	  private static ArrayList<ChildReference> retrieve1levelDirs(Drive service)throws IOException {
	    ArrayList<ChildReference> result = new ArrayList<ChildReference>();
	    com.google.api.services.drive.Drive.Children.List request= service.children().list("root");
	    do {
	      try {
	        ChildList dirs= request.execute();
	        result.addAll(dirs.getItems());
	        request.setPageToken(dirs.getNextPageToken());
	      } catch (IOException e) {
	        String errormessage="IOException: " + e;
	        if(errormessage.toLowerCase().contains("invalid credentials")){
	        	return null;
	        }
	        request.setPageToken(null);
	      }
	    } while (request.getPageToken() != null && request.getPageToken().length() > 0);
	    return result;
	  }
	  
	  
	/**
	   * Retrieve a list of File resources.
	   *
	   * @param service Drive API service instance.
	   * @return List of File resources.
	   * @throws IOException 
	*/
	  private static ArrayList<File> retrieveAllFiles(Drive service) throws IOException {
	    ArrayList<File> result = new ArrayList<File>();
	    Files.List request = service.files().list();
	    do {
	      try {
	        FileList files = request.execute();
	        result.addAll(files.getItems());
	        request.setPageToken(files.getNextPageToken());
	      } catch (IOException e) {
	        String errormessage="IOException: " + e;
	        if(errormessage.toLowerCase().contains("invalid credentials")){
	        	return null;
	        }else{
	        	System.out.println(errormessage);
	        }
	      }
	    } while (request.getPageToken() != null && request.getPageToken().length() > 0);
	    return result;
	  }
	  
	public static void listDirs() throws IOException{
		listDirs(1);
	}
	
	public static void listFiles() throws IOException{
		listFiles(1);
	}
	  
	public static HashMap<String,File> printDirsRecursively(HashMap<String,File>dirs,File dir,String ending) throws IOException{
		String title = dir.getTitle();
		List<ParentReference> parents = dir.getParents();
		for(ParentReference parentRef:parents){
			String parentId = parentRef.getId();
			File parent = service.files().get(parentId).execute();
			if(parent.getParents().size()==0){
				System.out.println(parent.getTitle()+"/"+title+"/"+ending);
			}else{
				printDirsRecursively(dirs, parent, title+"/"+ending+"("+dir.getMimeType()+")");
			}
		}
		return null;
	}
	
	public static void listDirs(int retryAttempts) throws IOException{
		System.out.println("Listing Dirs; Trying "+retryAttempts+" times");
		service = getDriveService();
		HashMap<String,File> dirs = retrieveAllDirs(service);
		if(dirs==null){
				if(retryAttempts-->0){
					restartService();
					listDirs(retryAttempts);
				}
				return;
		}
		for (File file :dirs.values()){ 
			printDirsRecursively(dirs, file,"");
		}
	}
	
	
	public static void listFiles(int retryAttempts) throws IOException{
		service = getDriveService();
		ArrayList<File> files = retrieveAllFiles(service);
		if(files==null){
				if(retryAttempts-->0){
					restartService();
					listFiles(retryAttempts);
				}
				return;
		}
		System.out.println(files.size()+" files found");
		for (File file:files){
			Long size = file.getFileSize();
			String id=file.getId();
			DateTime lastChangesDate = file.getModifiedDate();
			String title=file.getTitle();
			java.util.List<ParentReference> parents = file.getParents();
			String extension = file.getFileExtension();
			
			String parentsStr="\n====================";
			for(ParentReference parent:parents){
				String parentjson = parent.toPrettyString();
				String parent_id=parent.getId();
				String kind=parent.getKind();
				parentsStr+=parent_id+" "+parentjson+"         ";
			}
			System.out.println(title+"("+id+"  "+extension+")"+": "+parentsStr+"\n====================");
		}
	}
	
	private static Drive getDriveService() throws IOException{
		if(service==null){
			HttpTransport httpTransport = new NetHttpTransport();
			JacksonFactory jsonFactory = new JacksonFactory();
			Credential credential = getStoredCredentials(userEmail);
			try{
				service = new Drive.Builder(httpTransport, jsonFactory, credential).build();
			}catch(Error e){
				System.out.println( "Bad AUTHENTICATION: ");
				e.printStackTrace();
			}
		}
		return service;
	}
	
	public static File createCurrentDir() throws IOException{
		String dirName=System.getProperty("user.dir");
		String fileSeparator=System.getProperty("file.separator").replace("\\", "\\\\");
		System.out.println("dirname="+dirName);
		service = getDriveService();
		if(dirName.startsWith("/"))dirName=dirName.replaceFirst("/", "");
		String[] dirs= dirName.split(fileSeparator);
		ArrayList <File> alreadyCreatedDirs=new ArrayList <File> ();
		for(int i=0;i<dirs.length;i++){
			System.out.println("i="+i);
			File body = new File();
			body.setTitle(dirs[i]);
			File existingDir=findDir(alreadyCreatedDirs,dirs[i]);
			if(existingDir!=null){
				alreadyCreatedDirs.add(existingDir);
				continue;
			}
			body.setDescription("a Directory");
			body.setMimeType(folder_mime_type);
			if(i>0){
					body.setParents( Arrays.asList(new ParentReference().setId(alreadyCreatedDirs.get(i-1).getId())));
			}
			File file = service.files().insert(body).execute();
			System.out.println("File ID: " + file.getId());
			alreadyCreatedDirs.add(file);
		}
		return alreadyCreatedDirs.get(alreadyCreatedDirs.size()-1);
		
	}
	
	
	private static HashMap<String,File> getRemoteFiles(File currentDriveDir) throws IOException {
		getDriveService();
		HashMap<String, File> result=new HashMap<String, File>();
	    com.google.api.services.drive.Drive.Children.List request= service.children().list(currentDriveDir.getId());
	    ChildList driveFiles = request.execute();
		for(ChildReference driveFile:driveFiles.getItems()){ 
			Get gf = service.files().get(driveFile.getId());
			File df = gf.execute();
			result.put(df.getTitle(), df);
	    }
		return result;
		
		
	}
	
	 private static java.io.File downloadFile(File file) throws IOException {
		getDriveService();
		if (file.getDownloadUrl() != null && file.getDownloadUrl().length() > 0) {
		      try {
		        HttpResponse resp = service.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl())).execute();
		        InputStream inputStream = resp.getContent();
		        java.io.File result=null;
		        result=new java.io.File(file.getTitle());
				OutputStream out = new FileOutputStream(result);
				int read = 0;
				byte[] bytes = new byte[1024];
				while ((read = inputStream.read(bytes)) != -1) { out.write(bytes, 0, read); }
				inputStream.close();
				out.flush();
				out.close();
				System.out.println("New file "+file.getTitle()+"created!");
				return result;
		      } catch (IOException e) {
		        e.printStackTrace();
		        return null;
		      }
		} else {
		      return null;
		}
	}
	
	private static void uploadFile(java.io.File file, File currentDriveDir) throws IOException {
		service = getDriveService();
		File body = new File();
		body.setTitle(file.getName());
		body.setParents( Arrays.asList(new ParentReference().setId(currentDriveDir.getId())));
		//FileContent mediaContent = new FileContent("text/plain", file);
		FileContent mediaContent = new FileContent("text/plain", file);
		File resultFile= service.files().insert(body, mediaContent).execute();
	}
	
	public static void uploadTestFile() throws IOException{
		service = getDriveService();

		// Insert a file
		File body = new File();
		body.setTitle("My document");
		body.setDescription("A test document");
		body.setMimeType("text/plain");

		java.io.File fileContent = new java.io.File("document.txt");
		FileContent mediaContent = new FileContent("text/plain", fileContent);

		File file = service.files().insert(body, mediaContent).execute();
		System.out.println("File ID: " + file.getId());
	}
	



	/**
	 * Store OAuth 2.0 credentials in the application's database.
	 * 
	 * @param userId
	 *            User's ID.
	 * @param credentials
	 *            The OAuth 2.0 credentials to store.
	 */
	static void storeCredentials(String userId, Credential credentials) {
		// TODO: Implement this method to work with your database.
		// Store the credentials.getAccessToken() and
		// credentials.getRefreshToken()
		// string values in your database.
		throw new UnsupportedOperationException();
	}
	
	
    public static void setProperties(Properties props ){
    	for(Object key:props.keySet()){
			props.setProperty((String)key, (String)props.get(key));
		}
    }
    
    public static void saveProperties(Properties props )
    {
    	try {
    		props.store(new FileOutputStream("config.properties"), null);
    	} catch (IOException ex) {
    		ex.printStackTrace();
        }
    }
	
	static Properties getProperties() {
	   	Properties prop = new Properties();
    	try {
    		prop.load(new FileInputStream("config.properties"));
    		for(Object key:prop.keySet()){
    			System.out.println((String)key+"="+prop.getProperty((String)key));
    		}
    	} catch (IOException ex) {
    		System.out.println("Does your config.properties file exist?, maybe you need to create a blank one...");
        }
    	return prop;
 
    }
	
	static File findDir(ArrayList<File> alreadyCreatedDirs, String mydir) throws IOException{
		getDriveService();
		Files.List request = service.files().list().setQ("mimeType='"+folder_mime_type+"' and trashed=false and title='"+mydir+"'");
		FileList files = request.execute();
		File parent0 = null;
		try{ parent0=alreadyCreatedDirs.get(alreadyCreatedDirs.size()-1);
		}catch(Exception e){ }
		for(File found_file:files.getItems()){ //check if any of the found files has the same parent as our file
			List<ParentReference> parents1 = found_file.getParents();
			for(ParentReference parent:parents1){
				if(parent0==null){ 
					if(parent.getIsRoot())return found_file;
				}else if(parent.getId().equals(parent0.getId()))return(File)found_file;
			}
		}
		return null;
		
	}
	
	static HashMap<String,java.io.File> getCurrentLocalDirFiles(String dir){
		  HashMap<String,java.io.File> result=new HashMap<String,java.io.File>();
		  String name,path = "."; 
		  java.io.File folder = new java.io.File(path);
		  java.io.File[] listOfFiles = folder.listFiles(); 
		 
		  for (java.io.File file:listOfFiles){
		   if (file.isFile()){
			   name= file.getName();
			   result.put(file.getName(),file);
		   }
		  }
		return result;
	}
	
	

	private static void uploadCurrentDir() throws IOException {
		File currentDriveDir = createCurrentDir();
		HashMap<String, File> remoteFiles = getRemoteFiles(currentDriveDir);
		for(java.io.File file:getCurrentLocalDirFiles( System.getProperty("user.dir")).values() ){
			File rf = remoteFiles.get(file.getName().trim());
			if( rf ==null||(file.lastModified()>rf.getModifiedDate().getValue())){
				if(rf!=null){
					System.out.println("already existing file:"+file.getName()+" will be overriden");
				    service.files().delete(rf.getId()).execute();
				}
				uploadFile(file,currentDriveDir);
			}
		}
	}

	private static void downloadCurrentDir() throws IOException {
		File currentDriveDir = createCurrentDir();
		HashMap<String, File> remoteFiles = getRemoteFiles(currentDriveDir);
		HashMap<String, java.io.File> localFiles = getCurrentLocalDirFiles( System.getProperty("user.dir"));
		for(File file:remoteFiles.values() ){
			java.io.File lf=localFiles.get(file.getTitle().trim());
			if(lf==null ||(file.getModifiedDate().getValue()>lf.lastModified())){
				if(lf!=null)System.out.println("already existing file:"+file.getTitle()+" will be overriden");
				java.io.File df = downloadFile(file);
			}
		}
	}
	
	private static void ussage() throws URISyntaxException {
		java.io.File moduleFile = new java.io.File(DriveOperator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		System.out.println("The ussage of this program is:"+moduleFile.getName()+" <command> [-<option>]");
		System.out.println("\t available commands:\n\t\t  upload\n\t\t  download");
		System.out.println("\t available options:\n\t\t  -R (means Recursive -not yet implemented-)");
	}
	

	public static void main(String[] args) throws IOException, URISyntaxException{
		if( args.length<1 || args.length>2 ){
			ussage();
			return;
		}
		new DriveOperator();
		listFiles();
		//listDirs();
		for(String arg:args){
			switch(arg){
			case "upload":
				uploadCurrentDir();
				break;
			case "download":
				downloadCurrentDir();
				break;
			default: 
				ussage();
				break;
			}
		}
	}

}