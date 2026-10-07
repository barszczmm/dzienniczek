package io.github.barszczmm.dzienniczek.di

import io.github.barszczmm.dzienniczek.notifications.MessageChecker
import io.github.barszczmm.dzienniczek.platform.createHttpClient
import io.github.barszczmm.dzienniczek.session.ApiSession
import io.github.barszczmm.dzienniczek.session.SessionStorage
import io.github.barszczmm.dzienniczek.session.createSessionDataStore
import io.github.barszczmm.dzienniczek.viewmodel.AccountViewModel
import io.github.barszczmm.dzienniczek.viewmodel.AnnouncementsViewModel
import io.github.barszczmm.dzienniczek.viewmodel.DashboardViewModel
import io.github.barszczmm.dzienniczek.viewmodel.ExamsViewModel
import io.github.barszczmm.dzienniczek.viewmodel.GradesViewModel
import io.github.barszczmm.dzienniczek.viewmodel.HomeworkViewModel
import io.github.barszczmm.dzienniczek.viewmodel.LoginViewModel
import io.github.barszczmm.dzienniczek.viewmodel.MessageDetailsViewModel
import io.github.barszczmm.dzienniczek.viewmodel.MessagesViewModel
import io.github.barszczmm.dzienniczek.viewmodel.NotesViewModel
import io.github.barszczmm.dzienniczek.viewmodel.SelectStudentsViewModel
import io.github.barszczmm.dzienniczek.viewmodel.StartViewModel
import io.github.barszczmm.dzienniczek.viewmodel.TimetableViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    includes(platformModule)

    single { ApiSession() }
    single { createHttpClient() }
    single { createSessionDataStore() }
    single { SessionStorage(get(), get()) }
    single { MessageChecker(get(), get()) }

    viewModel { LoginViewModel(get(), get(), get()) }
    viewModel { SelectStudentsViewModel(get(), get(), get()) }
    viewModel { AccountViewModel(get(), get()) }
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { GradesViewModel(get()) }
    viewModel { TimetableViewModel(get()) }
    viewModel { ExamsViewModel(get()) }
    viewModel { HomeworkViewModel(get()) }
    viewModel { NotesViewModel(get()) }
    viewModel { AnnouncementsViewModel(get()) }
    viewModel { MessagesViewModel(get()) }
    viewModel { MessageDetailsViewModel(get()) }
    viewModel { StartViewModel(get()) }
}
