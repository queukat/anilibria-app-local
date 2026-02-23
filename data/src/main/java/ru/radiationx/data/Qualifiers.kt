package ru.radiationx.data

import javax.inject.Qualifier

@Qualifier
annotation class DataPreferences

@Qualifier
annotation class SecureDataPreferences

@Qualifier
annotation class CriticalSecureDataPreferences

@Qualifier
annotation class ApiClient

@Qualifier
annotation class MainClient

@Qualifier
annotation class SimpleClient
