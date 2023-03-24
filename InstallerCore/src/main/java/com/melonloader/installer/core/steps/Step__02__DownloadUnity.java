package com.melonloader.installer.core.steps;

import com.melonloader.installer.core.InstallerStep;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Step__02__DownloadUnity extends InstallerStep {
    @Override
    public boolean Run() throws Exception {
        if (properties.unityNativeBase != null && properties.unityManagedBase != null)
            return true;

        String outputPath = paths.unityZip.toString();

        // The data folder check isn't *really* needed but it doesn't hurt
        if (!outputPath.contains("com.melonloader.installer") && Files.exists(Paths.get(outputPath))) {
            properties.logger.Log("Using local Unity dependencies!");
            return true;
        }

        properties.logger.Log("Downloading Unity Dependencies");
        downloadFile(properties.unityProvider + properties.unityVersion + ".zip", outputPath);

        return true;
    }

    protected void downloadFile(String _url, String _output) throws IOException {
        properties.logger.Log("Downloading [" + _url + "]");

        URL url = new URL(_url);
        URLConnection connection = url.openConnection();
        connection.connect();

        int lenghtOfFile = connection.getContentLength();

        // download the file
        InputStream input = new BufferedInputStream(url.openStream(),
                8192);

        // Output stream
        OutputStream output = new FileOutputStream(_output);

        byte data[] = new byte[1024];

        int count;
        while ((count = input.read(data)) != -1) {
            output.write(data, 0, count);
        }

        output.flush();

        // closing streams
        output.close();
        input.close();
    }
}
