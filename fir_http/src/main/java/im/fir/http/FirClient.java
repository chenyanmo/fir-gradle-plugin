package im.fir.http;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import im.fir.module.App;
import im.fir.module.Binary;
import im.fir.module.Cert;
import im.fir.module.Icon;
import im.fir.module.Mapping;
import im.fir.module.UploadInfo;
import im.fir.module.User;


public class FirClient {
    private static final Log log = LogFactory.getLog(FirClient.class);

    private static final String GET_USER_INFO = "http://api.fir.im/user";
    private static final String UPLOAD_MAPPING = "http://api.bughd.com/full_versions";
    private static final String CREATE_VERSION = "http://api.bughd.com/projects";
    private static final String GET_UPLOAD_INFO = "http://api.fir.im/apps";
    private static final String POST_QINIU_CALLBACK = "http://api.fir.im/auth/";
    private String mAppPath;
    private HttpClient httpClient;

    private static final String FIR_GRADLE_PLUGIN_VERSION = "1.0.7";
    private static final String FIR_GRADLE_PLUGIN_SOURCE = "fir-gradle-plugin";
    private static final int STATUSCODE_SUCCESS = 200;
    private static final int STATUSCODE_COMPLETED = 201;

//	public FirClient() {
//
//
//	}

    public UploadInfo getUploadInfo(String type, String bundleId, String token) throws FirDeployException {
        HttpPost httpPost = new HttpPost("http://api.fir.im/apps");
        try {
            httpPost.setHeader("source", FIR_GRADLE_PLUGIN_SOURCE);
            httpPost.setHeader("version", FIR_GRADLE_PLUGIN_VERSION);
            MultipartEntity entity = new MultipartEntity();
            addParam(entity, "api_token", token);
            addParam(entity, "type", type);
            addParam(entity, "bundle_id", bundleId);
            addParam(entity, "manual_callback", "1");
            httpPost.setEntity(entity);
            HttpResponse response = this.httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            log.info("==   statusCode " + statusCode);
            System.out.println("==  getUploadInfo statusCode " + statusCode);
            switch (statusCode) {
                case 401:
                    throw createExceptionWithFirErrorResponse(response, "");
            }
            String json = IOUtils.toString(response.getEntity().getContent());
            log.info("==   Upload QINIU json " + json);
            UploadInfo uploadForm = UploadInfo.createFormJson(json);
            log.info("==   icon Token " + uploadForm.getCert().getIcon().getToken());
            return uploadForm;
        } catch (FirDeployException e) {
            throw e;
        } catch (Exception e) {
            throw new FirDeployException("Error while get details for update id = " + bundleId, e);
        } finally {
            resetHttpConnection();
        }
    }

    public HttpPost createCallbackHttpPost(String url, String build, String fname, String key, String name, String parent_id, String fsize, String release_type, String distribution_name, String token, String version, String changelog, String user_id) throws
            UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(url);

        httpPost.setHeader("source", FIR_GRADLE_PLUGIN_SOURCE);
        httpPost.setHeader("version", FIR_GRADLE_PLUGIN_VERSION);

        MultipartEntity entity = new MultipartEntity();
        addParam(entity, "build", build);
        addParam(entity, "fname", fname);
        addParam(entity, "key", key);
        addParam(entity, "name", name);
        addParam(entity, "origin", "fir-cli");
        addParam(entity, "parent_id", parent_id);
        addParam(entity, "release_tag", "develop");
//        addParam(entity, "fsize", fsize);
        addParam(entity, "release_type", release_type);
        addParam(entity, "distribution_name", distribution_name);
        addParam(entity, "token", token);
        addParam(entity, "version", version);
        addParam(entity, "changelog", changelog);
        addParam(entity, "user_id", user_id);
        httpPost.setEntity(entity);

        return httpPost;
    }

    protected void addParam(MultipartEntity entity, String paramName, String paramValue)
            throws UnsupportedEncodingException {
        entity.addPart(paramName, new StringBody(paramValue, "text/plain", Charset.forName("UTF-8")));
    }

    void resetHttpConnection() {
        if (this.httpClient != null) {
            this.httpClient.getConnectionManager().shutdown();
        }
        this.httpClient = new DefaultHttpClient();
    }

//	private void log(String string) {
//		if (this.logger != null) {
//			this.logger.println(string);
//		}
//	}

    public User doCheckToken(String token) throws IOException {
        resetHttpConnection();
        HttpGet httpGet = new HttpGet("http://api.fir.im/user?token=" + token);
        httpGet.setHeader("source", FIR_GRADLE_PLUGIN_SOURCE);
        httpGet.setHeader("version", FIR_GRADLE_PLUGIN_VERSION);
        HttpResponse response = this.httpClient.execute(httpGet);

        int statusCode = response.getStatusLine().getStatusCode();
        log.info("upload app statecode" + statusCode);
        System.out.println("==  doCheckToken statusCode " + statusCode);
        if (statusCode == STATUSCODE_SUCCESS) {
            String json = IOUtils.toString(response.getEntity().getContent());
            log.info("==   GET USER INFO == " + json);
            User user = User.createFormJson(json);
            log.info("==   USER EMAIL ==" + user.getEmail());
            return user;
        }
        return null;
    }

    public String deployFile(App app, Mapping mapping, String apiToken)
            throws FirDeployException {
        log.info("== Deploy file to fir.im");
        log.info("== reseting http connection");
        resetHttpConnection();

//		log("==   Ask for upload information");
        System.out.println(app.getAppPath());

        UploadInfo uploadForm = getUploadInfo(app.getAppType(), app.getBundleId(), apiToken);

        log.info("==   Upload app file " + app.getAppPath());

        int code = uploadApp(uploadForm, app);
        log.info("==   Upload app file stateCode" + code);
        System.out.println("==  deployFile statusCode " + code);
        if (code == STATUSCODE_SUCCESS) {
            log.info("==   Upload icon file " + app.getAppPath());
            if (app.getIconPath() != null) {
                int c = uploadIcon(uploadForm, app.getIconPath());
                log.info("==   Upload icon file stateCode" + c);
            }
            if (mapping != null) {
                deployMappingFile(app, mapping);
            }

            int statusCode = getQiniuCallback(uploadForm, app);

            return uploadForm.getShort();
        }
        return null;
    }

    public int deployMappingFile(App app, Mapping mapping) throws FirDeployException {
        log.info("== Deploy mapping to bughd.com");
        log.info("== reseting http connection");
        resetHttpConnection();
        return uploadMapping(mapping, app);
    }

    protected FirDeployException createExceptionWithFirErrorResponse(HttpResponse response, String prefix)
            throws ParseException, IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        String cause = "";
        switch (statusCode) {
            case 404:
                cause = "resource not found (404)";
                break;
            case 401:
                String json = readBodyResponse(response);
                try {
                    FirErrors errors = FirErrors.createFromJson(json);
                    cause = errors.toString();
                } catch (Exception e) {
                    cause = json;
                }
        }
        return new FirDeployException(prefix + cause);
    }

    protected int uploadApp(UploadInfo uploadInfo, App app) throws FirDeployException {
        try {
            Cert cert = uploadInfo.getCert();

            HttpPost httppost = createAppHttpPost(cert.getBinary(), app);
            HttpResponse response = this.httpClient.execute(httppost);

            int statusCode = response.getStatusLine().getStatusCode();
            log.info("upload app statecode" + statusCode);
            System.out.println("==  uploadApp statusCode " + statusCode);
            String message;
            if (statusCode != STATUSCODE_SUCCESS) {
                message = IOUtils.toString(response.getEntity().getContent());
                throw new FirDeployException("Impossible to upload file " + app.getAppPath() + ": " + message);
            }
            return statusCode;
        } catch (FirDeployException e) {
            throw e;
        } catch (Exception e) {
            throw new FirDeployException("Error while uploading " + app.getAppPath() + " : " + e.getMessage(), e);
        } finally {
            resetHttpConnection();
        }
    }

    public int getQiniuCallback(UploadInfo uploadInfo, App app) throws FirDeployException {
        try {
            Cert cert = uploadInfo.getCert();

            String build = "" + app.getBuild();
            String fname = cert.getBinary().getKey();
            String key = cert.getBinary().getKey();
            String name = app.getName();
            String parent_id = uploadInfo.getId();
//            String fsize = "684729";
            String release_type = app.getReleaseType();
            String distribution_name = "";
            String token = cert.getBinary().getToken();
            String version = app.getBuild();
            String changelog = app.getChangeLog();
            String user_id = uploadInfo.getmAppUserId();
//            String url = POST_QINIU_CALLBACK + uploadInfo.getStorage() + "/callback";
            String url = POST_QINIU_CALLBACK + "qiniu" + "/callback";


            HttpPost httppost = createCallbackHttpPost(url, build, fname, key, name, parent_id, fsize, release_type, distribution_name, token, version, changelog, user_id);
            HttpResponse response = this.httpClient.execute(httppost);

            int statusCode = 0;
            statusCode = response.getStatusLine().getStatusCode();
            log.info("Qiniu Callback statecode" + statusCode);
            System.out.println("==  QiniuCallback statusCode " + statusCode);
            String message;
            if (statusCode != STATUSCODE_SUCCESS) {
                message = IOUtils.toString(response.getEntity().getContent());
                throw new FirDeployException("Impossible to upload file " + app.getAppPath() + ": " + message);
            }
            return statusCode;
        } catch (FirDeployException e) {
            throw e;
        } catch (Exception e) {
            throw new FirDeployException("Error while uploading " + app.getAppPath() + " : " + e.getMessage(), e);
        } finally {
            resetHttpConnection();
        }
    }

    public int uploadMapping(Mapping mapping, App app) throws FirDeployException {
        try {
            HttpPost httpPost = createMappingHttpPost(mapping, app);
            HttpResponse response = this.httpClient.execute(httpPost);

            int statusCode = response.getStatusLine().getStatusCode();
            log.info("upload app statecode" + statusCode);
            System.out.println("==  uploadMapping statusCode " + statusCode);
            String message;
            if (statusCode != HttpStatus.SC_CREATED) {
                message = IOUtils.toString(response.getEntity().getContent());
                throw new FirDeployException("Impossible to upload file " + mapping.getFilePath() + ": " + message);
            }
            return statusCode;
        } catch (FirDeployException e) {
            throw e;
        } catch (Exception e) {
            throw new FirDeployException("Error while uploading " + mapping.getFilePath() + " : " + e.getMessage(), e);
        } finally {
            resetHttpConnection();
        }
    }

    // protected int createVersion(App app, Mapping mapping) throws
    // FirDeployException {
    // try {
    // HttpPost httpPost = createMappingHttpPost(mapping.getProjectId(),
    // app.getVersion(), app.getBuild(),
    // mapping.getApiToken());
    // HttpResponse response = this.httpClient.execute(httpPost);
    //
    // int statusCode = response.getStatusLine().getStatusCode();
    // log("Create version success=====" + statusCode);
    // String message;
    // if (statusCode != STATUSCODE_SUCCESS) {
    // message = IOUtils.toString(response.getEntity().getContent());
    // throw new FirDeployException("Create version failed ==== " + message);
    // }
    // return statusCode;
    // } catch (FirDeployException e) {
    // throw e;
    // } catch (Exception e) {
    // throw new FirDeployException("Error create version======= " +
    // app.getVersion() + " : " + e.getMessage(), e);
    // } finally {
    // resetHttpConnection();
    // }
    // }

    protected int uploadIcon(UploadInfo uploadInfo, String iconPath) throws FirDeployException {
        try {
            Cert cert = uploadInfo.getCert();

            HttpPost httppost = createIconHttpPost(cert.getIcon(), iconPath);
            HttpResponse response = this.httpClient.execute(httppost);

            int statusCode = response.getStatusLine().getStatusCode();
            log.info("upload icon statecode" + statusCode);
            System.out.println("==  uploadIcon statusCode " + statusCode);
            String message;
            if (statusCode != STATUSCODE_SUCCESS) {
                message = IOUtils.toString(response.getEntity().getContent());
                throw new FirDeployException("Impossible to upload file " + iconPath + ": " + message);
            }
            return statusCode;
        } catch (FirDeployException e) {
            throw e;
        } catch (Exception e) {
            throw new FirDeployException("Error while uploading " + iconPath + " : " + e.getMessage(), e);
        } finally {
            resetHttpConnection();
        }
    }

    protected String readBodyResponse(HttpResponse response) throws ParseException, IOException {
        return EntityUtils.toString(response.getEntity(), "UTF-8");
    }

    protected HttpPost createAppHttpPost(Binary binary, App app) throws
            UnsupportedEncodingException {
        MultipartEntity entity = new MultipartEntity();
        addParam(entity, "key", binary.getKey());
        addParam(entity, "token", binary.getToken());
        addParam(entity, "x:name", app.getName());
        addParam(entity, "x:version", app.getVersion());
        log.info("x:name" + app.getName());
        log.info("x:version" + app.getVersion());
        addParam(entity, "x:build", app.getBuild());
        log.info("x:build" + app.getBuild());

        addParam(entity, "x:release_type", app.getReleaseType());
        addParam(entity, "x:changelog", app.getChangeLog());
        ContentBody cbFile = new FileBody(new File(app.getAppPath()));
        entity.addPart("file", cbFile);
        HttpPost httpPost = new HttpPost(binary.getUpLoadUrl());
        httpPost.setHeader("source", FIR_GRADLE_PLUGIN_SOURCE);
        httpPost.setHeader("version", FIR_GRADLE_PLUGIN_VERSION);
        httpPost.setEntity(entity);
        return httpPost;
    }

    protected HttpPost createMappingHttpPost(Mapping mapping, App app) throws
            UnsupportedEncodingException {
        MultipartEntity entity = new MultipartEntity();
        addParam(entity, "version", app.getVersion());
        addParam(entity, "build", app.getBuild());
        addParam(entity, "api_token", mapping.getApiToken());
        ContentBody cbFile = new FileBody(new File(mapping.getFilePath()));
        entity.addPart("file", cbFile);
        System.out.println("version===" + app.getVersion());
        System.out.println("build===" + app.getBuild());
        System.out.println("token===" + mapping.getApiToken());


        String url = "http://api.bughd.com/projects/" + mapping.getProjectId() + "/full_versions";
        System.out.println(url);
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("source", FIR_GRADLE_PLUGIN_SOURCE);
        httpPost.setHeader("version", FIR_GRADLE_PLUGIN_VERSION);
        httpPost.setEntity(entity);
        return httpPost;
    }

    // protected HttpPost createMappingHttpPost(Mapping mapping) throws
    // UnsupportedEncodingException {
    // MultipartEntity entity = new MultipartEntity();
    // String id = mapping.getId();
    // String token = mapping.getApiToken();
    // addParam(entity, "id", id);
    // addParam(entity, "api_token", token);
    // addParam(entity, "project_id", mapping.getProjectId());
    // ContentBody cbFile = new FileBody(new File(mapping.getFilePath()));
    // entity.addPart("file", cbFile);
    // HttpPost httpPost = new HttpPost("http://noodles.bughd.com/api/projects/"
    // + id + "/full_versions");
    // httpPost.setEntity(entity);
    // return httpPost;
    // }

    protected HttpPost createIconHttpPost(Icon icon, String iconPath)
            throws UnsupportedEncodingException {
        MultipartEntity entity = new MultipartEntity();
        ContentBody cbFile = new FileBody(new File(iconPath));
        addParam(entity, "key", icon.getKey());
        addParam(entity, "token", icon.getToken());
        entity.addPart("file", cbFile);
        HttpPost httpPost = new HttpPost(icon.getUpLoadUrl());
        httpPost.setHeader("source", FIR_GRADLE_PLUGIN_SOURCE);
        httpPost.setHeader("version", FIR_GRADLE_PLUGIN_VERSION);
        httpPost.setEntity(entity);
        return httpPost;
    }


}
