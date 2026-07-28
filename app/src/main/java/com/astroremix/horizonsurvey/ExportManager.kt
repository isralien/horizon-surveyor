package com.astroremix.horizonsurvey

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.astroremix.horizonsurvey.core.HorizonProfile
import com.astroremix.horizonsurvey.core.StellariumHorizonExporter
import java.io.File

/** Renders a survey to disk and wraps the result in a share [Intent]. */
object ExportManager {

    fun export(context: Context, profile: HorizonProfile, surveyName: String): Intent {
        val safeName = surveyName
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "horizon" }

        val horizonFileName = "${safeName}_horizon.txt"
        val exportDir = File(context.getExternalFilesDir("horizon_exports"), safeName)
        exportDir.mkdirs()

        val horizonFile = File(exportDir, horizonFileName)
        horizonFile.writeText(StellariumHorizonExporter.buildHorizonTxt(profile, surveyName))

        val iniFile = File(exportDir, "landscape.ini")
        iniFile.writeText(
            StellariumHorizonExporter.buildLandscapeIni(
                landscapeName = surveyName,
                horizonFileName = horizonFileName,
                description = "Surveyed on-site with the AstroRemix Horizon Surveyor app.",
            )
        )

        val csvFile = File(exportDir, "${safeName}_horizon.csv")
        csvFile.writeText(StellariumHorizonExporter.buildAzAltCsv(profile))

        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList(
            listOf(horizonFile, iniFile, csvFile).map { FileProvider.getUriForFile(context, authority, it) }
        )

        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
