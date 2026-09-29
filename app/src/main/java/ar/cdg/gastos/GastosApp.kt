package ar.cdg.gastos

import android.app.Application
import ar.cdg.gastos.data.Repository

class GastosApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
    }
}
