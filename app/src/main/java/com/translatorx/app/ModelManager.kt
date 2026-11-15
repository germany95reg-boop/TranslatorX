package com.translatorx.app

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.tasks.await

class ModelManager {
    private val manager = RemoteModelManager.getInstance()

    suspend fun installed(): Set<String> =
        manager.getDownloadedModels(TranslateRemoteModel::class.java).await()
            .map { it.language }.toSet()

    suspend fun download(tag: String) {
        val lang = requireNotNull(TranslateLanguage.fromLanguageTag(tag))
        manager.download(TranslateRemoteModel.Builder(lang).build(), DownloadConditions.Builder().build()).await()
    }

    suspend fun delete(tag: String) {
        val lang = requireNotNull(TranslateLanguage.fromLanguageTag(tag))
        manager.deleteDownloadedModel(TranslateRemoteModel.Builder(lang).build()).await()
    }
}
