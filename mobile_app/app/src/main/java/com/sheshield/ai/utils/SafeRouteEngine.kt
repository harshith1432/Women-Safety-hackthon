package com.sheshield.ai.utils

import com.sheshield.ai.data.remote.Route
import com.sheshield.ai.data.remote.Step
import java.util.Calendar

class SafeRouteEngine {

    companion object {
        // Safety Weights from User Requirements
        private const val WEIGHT_HIGHWAY = 1
        private const val WEIGHT_MAIN_ROAD = 2
        private const val WEIGHT_HOSPITAL = 2
        private const val WEIGHT_POLICE = 1
        private const val WEIGHT_SMALL_ROAD = 5
        private const val WEIGHT_ISOLATED_ROAD = 8
        private const val WEIGHT_UNSAFE_ZONE = 10
        private const val WEIGHT_NIGHT_UNSAFE = 12

        /**
         * Calculates the safety score for a given route.
         * Lower score means safer route.
         */
        fun calculateSafetyScore(route: Route): Double {
            var totalWeight = 0.0
            var totalDistance = 0.0

            route.legs.forEach { leg ->
                leg.steps.forEach { step ->
                    val stepWeight = determineStepWeight(step)
                    val stepDistance = step.distance.value.toDouble() / 1000.0 // in km
                    
                    totalWeight += stepWeight * stepDistance
                    totalDistance += stepDistance
                }
            }

            // Normalize by distance to compare routes of different lengths
            return if (totalDistance > 0) totalWeight / totalDistance else Double.MAX_VALUE
        }

        private fun determineStepWeight(step: Step): Int {
            val instructions = step.html_instructions?.lowercase() ?: ""
            val isNight = isNightTime()

            return when {
                instructions.contains("police") || instructions.contains("thana") || instructions.contains("chowki") -> WEIGHT_POLICE
                instructions.contains("hospital") || instructions.contains("clinic") || instructions.contains("medical") -> WEIGHT_HOSPITAL
                instructions.contains("highway") || instructions.contains("expressway") -> WEIGHT_HIGHWAY
                instructions.contains("main road") || instructions.contains("avenue") || instructions.contains("boulevard") -> WEIGHT_MAIN_ROAD
                instructions.contains("unsafe") || instructions.contains("danger") || instructions.contains("crime") -> WEIGHT_UNSAFE_ZONE
                instructions.contains("isolated") || instructions.contains("narrow") -> if (isNight) WEIGHT_NIGHT_UNSAFE else WEIGHT_ISOLATED_ROAD
                instructions.contains("small road") || instructions.contains("alley") -> WEIGHT_SMALL_ROAD
                // Default to a moderate weight if unknown
                else -> 3
            }
        }

        /**
         * Heuristic to determine if it's currently night time.
         * (Could be improved with local sunset/sunrise data)
         */
        private fun isNightTime(): Boolean {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return hour < 6 || hour > 19
        }

        /**
         * Selects the safest route from a list of alternative routes.
         */
        fun findSafestRoute(routes: List<Route>): Route? {
            if (routes.isEmpty()) return null
            
            return routes.minByOrNull { calculateSafetyScore(it) }
        }
    }
}
