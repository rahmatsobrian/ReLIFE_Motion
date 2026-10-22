package com.siroha.relifemotion

import android.app.Application
import com.siroha.relifemotion.data.ProjectRepo
import com.siroha.relifemotion.data.SettingsStore
import com.siroha.relifemotion.priv.Privileged

object Graph {
    lateinit var app: Application
    lateinit var settings: SettingsStore
    lateinit var repo: ProjectRepo
}

class ReLifeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.app = this
        Graph.settings = SettingsStore(this)
        Graph.repo = ProjectRepo(this)
        Privileged.init(this)
    }
}
