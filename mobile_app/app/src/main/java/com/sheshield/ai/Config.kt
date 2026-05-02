package com.sheshield.ai

object Config {
    /**
     * CONNECTION SETTINGS
     */
    const val BACKEND_URL = "https://posting-stroke-committee-publishing.trycloudflare.com/"
    
    const val SYNC_INTERVAL_MS = 15000L // Sync every 15 seconds for faster testing

    // TWILIO SETTINGS
    const val TWILIO_ACCOUNT_SID = "YOUR_TWILIO_ACCOUNT_SID" // TODO: Add your Twilio SID
    const val TWILIO_AUTH_TOKEN = "YOUR_TWILIO_AUTH_TOKEN" // TODO: Add your Twilio Auth Token
    const val TWILIO_FROM_NUMBER = "+19708220590"
    const val TWILIO_BASE_URL = "https://api.twilio.com/2010-04-01/"
}
