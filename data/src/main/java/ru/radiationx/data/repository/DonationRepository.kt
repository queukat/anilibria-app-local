package ru.radiationx.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.radiationx.data.datasource.holders.DonationHolder
import ru.radiationx.data.datasource.remote.api.DonationApi
import ru.radiationx.data.entity.domain.donation.DonationInfo
import ru.radiationx.data.entity.domain.donation.yoomoney.YooMoneyDialog
import ru.radiationx.data.entity.mapper.toDomain
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

class DonationRepository
    @Inject
    constructor(
        private val donationApi: DonationApi,
        private val donationHolder: DonationHolder,
    ) {
        suspend fun requestUpdate() =
            withContext(AppDispatchers.io) {
                donationApi
                    .getDonationDetail()
                    .also { donationHolder.save(it) }
            }

        fun observerDonationInfo(): Flow<DonationInfo> =
            donationHolder
                .observe()
                .map { it.toDomain() }
                .flowOn(AppDispatchers.io)

        suspend fun createYooMoneyPayLink(
            amount: Int,
            type: String,
            form: YooMoneyDialog.YooMoneyForm,
        ): String =
            donationApi.createYooMoneyPayLink(amount, type, form)
    }
