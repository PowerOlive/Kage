package com.absinthe.kage.device.server

import android.os.Environment
import android.util.Log
import com.absinthe.kage.connect.protocol.Config
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class KageServer : NanoHTTPD(Config.HTTP_SERVER_PORT) {

    override fun serve(session: IHTTPSession): Response {
        var filepath = session.uri.trim { it <= ' ' }
        val rootDir = Environment.getExternalStorageDirectory()
        val filesList: Array<File>?

        Log.d(TAG, "Session Uri = $filepath")
        if (filepath.trim { it <= ' ' }.isEmpty() || filepath.trim { it <= ' ' } == ROOT_DIR) {
            filepath = rootDir.absolutePath
        }
        filesList = File(filepath).listFiles()
        return filesList?.let { responsePage(it) } ?: responseFile(filepath)
    }

    //对于请求目录的，返回文件列表
    private fun responsePage(filesList: Array<File>): Response {
        val builder = StringBuilder()
        builder.append("<!DOCTYPER html><html><body>")
        builder.append("<ol>")
        for (detailsOfFiles in filesList) {
            builder.append("<a href=\"")
                    .append(detailsOfFiles.absolutePath).append("\" alt = \"\">")
                    .append(detailsOfFiles.absolutePath).append("</a><br>")
        }
        builder.append("</ol>")
        builder.append("</body></html>\n")
        //回送应答
        return newFixedLengthResponse(builder.toString())
    }

    //对于请求文件的，返回下载的文件
    private fun responseFile(uri: String): Response {
        try {
            //文件输入流
            val fis = FileInputStream(uri)
            // 返回OK，同时传送文件
            return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", fis, fis.available().toLong())
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return responseNotExist(uri)
    }

    //页面不存在，或者文件不存在时
    private fun responseNotExist(url: String): Response {
        val builder = StringBuilder()
        builder.append("<!DOCTYPE html><html>body>")
        builder.append("Sorry,Can't Found").append(url).append(" !")
        builder.append("</body></html>\n")

        return newFixedLengthResponse(builder.toString())
    }

    companion object {
        private val TAG = KageServer::class.java.simpleName
        private const val ROOT_DIR = "/"
    }
}