package healthstack.wearable.support.data

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import healthstack.common.HEALTH_DATA_FOLDER_NAME
import healthstack.common.MessageConfig.MOBILE_RESEARCH_APP_CAPABILITY
import healthstack.common.model.PpgGreen
import healthstack.common.model.PrivDataType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class DataSender(private val context: Context) {
    val gson = Gson()
    suspend fun sendFile() {
        val data: Collection<PpgGreen> = listOf(
            PpgGreen(12300, 60),
            PpgGreen(12345, 80),
            PpgGreen(1234567, 100),
        )

        val outputDir = "${context.filesDir}" + HEALTH_DATA_FOLDER_NAME
        Files.createDirectories(Paths.get(outputDir))

        data.groupBy { it.timestamp / splitInterval * splitInterval }.forEach {
                (timestamp, group) ->
            val file = File(outputDir, "$timestamp-${timestamp + splitInterval}-${PpgGreen::class.java.simpleName}.csv")

//            val healthData = group.joinToString("\n") { target ->
//                target.javaClass.kotlin.memberProperties.map {
//                    gson.toJson(it.get(target))
//                }.joinToString("|")
//            }

            runCatching {
                if (!file.exists()) {
                    FileOutputStream(file, true).use {
                        output ->
                            output.write("WEAR_PPG_GREEN\nppg|timestamp\n0|1234567789123456\n77|-1234567890123456".toByteArray())
//                        output.write(PrivDataType.fromModel(PpgGreen::class).name.toByteArray() + "\n".toByteArray())
//                        output.write(PpgGreen::class.memberProperties.joinToString("|") { it.name }.toByteArray() + "\n".toByteArray())
                    }
                }
//                FileOutputStream(file, true).use { output ->
//                    output.write(healthData.toByteArray() + "\n".toByteArray())
//                }
            }.onFailure {
                it.printStackTrace()
            }

            withContext(Dispatchers.IO) {
                runCatching {
                    val nodes = getCapabilityNodes()
                    if (nodes.size != 1) throw IllegalStateException("The nodes size is not 1. Node Size: ${nodes.size}")
                    val channelClient = Wearable.getChannelClient(context)

                    val channelCallback = object : ChannelClient.ChannelCallback() {
                        override fun onChannelClosed(
                            channel: ChannelClient.Channel,
                            closeReason: Int,
                            p2: Int,
                        ) {
                            super.onChannelClosed(channel, closeReason, p2)
                            Log.i(TAG, "onChannelClosed ${channel.path}, closeReason: $closeReason")
                            if (closeReason == CLOSE_REASON_REMOTE_CLOSE) {
                                Log.i(TAG, "onSuccess")
                                // onSuccess()
                            }
                            channelClient.unregisterChannelCallback(this)
                        }
                    }
                    val channel = Tasks.await(channelClient.openChannel(nodes.first().id, file.name))
                    Tasks.await(channelClient.registerChannelCallback(channel, channelCallback))
                    Tasks.await(channelClient.sendFile(channel, Uri.fromFile(file)))
                    return@withContext
                }.onFailure {
                    Log.d(TAG, it.stackTraceToString())
                    Log.e(TAG, it.message ?: "")
                }
            }
        }
    }

    suspend fun sendData(
        serializableData: Any,
        privDataType: PrivDataType,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val nodes = getCapabilityNodes()
            if (nodes.size != 1) throw IllegalStateException("The nodes size is not 1. Node Size: ${nodes.size}")

            val channelClient = Wearable.getChannelClient(context)
            val channel = Tasks.await(channelClient.openChannel(nodes.first().id, privDataType.messagePath))
            val message = Gson().toJson(serializableData).toByteArray()

            Tasks.await(channelClient.getOutputStream(channel)).use { it.write(message) }
        }.onFailure {
            Log.d(TAG, it.stackTraceToString())
            Log.e(TAG, it.message ?: "")
        }
    }

    suspend fun isConnected(): Boolean {
        return getCapabilityNodes().size == 1
    }

    private fun getCapabilityNodes(): List<Node> {
        val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)
        val capabilityInfoTask = capabilityClient.getCapability(
            MOBILE_RESEARCH_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE
        )
        return Tasks.await(capabilityInfoTask, 5, TimeUnit.SECONDS).nodes.toList()
    }

    companion object {
        private val TAG = DataSender::class.simpleName
        const val splitInterval = DateUtils.HOUR_IN_MILLIS
    }
}
