package com.kgconsol.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kgconsol.presentation.batch.BatchListScreen
import com.kgconsol.presentation.box.BoxScreen
import com.kgconsol.presentation.reports.ReportsScreen
import com.kgconsol.presentation.scan.ScanScreen
import com.kgconsol.presentation.settings.SettingsScreen

object Routes {
    const val BATCH_LIST = "batches"
    const val BOX = "box/{batchId}/{boxId}"
    const val SCAN = "scan/{boxId}"
    const val SETTINGS = "settings"
    const val REPORTS = "reports/{batchId}"

    fun box(batchId: Long, boxId: Long) = "box/$batchId/$boxId"
    fun scan(boxId: Long) = "scan/$boxId"
    fun reports(batchId: Long) = "reports/$batchId"
}

@Composable
fun KGNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.BATCH_LIST) {

        composable(Routes.BATCH_LIST) {
            BatchListScreen(
                onOpenBox = { batchId, boxId ->
                    navController.navigate(Routes.box(batchId, boxId))
                },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.BOX,
            arguments = listOf(
                navArgument("batchId") { type = NavType.LongType },
                navArgument("boxId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val batchId = backStackEntry.arguments!!.getLong("batchId")
            val boxId = backStackEntry.arguments!!.getLong("boxId")
            BoxScreen(
                batchId = batchId,
                boxId = boxId,
                onScan = { navController.navigate(Routes.scan(boxId)) },
                onBoxCompleted = { newBoxId ->
                    navController.navigate(Routes.box(batchId, newBoxId)) {
                        popUpTo(Routes.BATCH_LIST)
                    }
                },
                onReports = { navController.navigate(Routes.reports(batchId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.SCAN,
            arguments = listOf(navArgument("boxId") { type = NavType.LongType })
        ) { backStackEntry ->
            val boxId = backStackEntry.arguments!!.getLong("boxId")
            ScanScreen(
                boxId = boxId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.REPORTS,
            arguments = listOf(navArgument("batchId") { type = NavType.LongType })
        ) { backStackEntry ->
            val batchId = backStackEntry.arguments!!.getLong("batchId")
            ReportsScreen(
                batchId = batchId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
