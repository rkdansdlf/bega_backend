package com.example.common.readonly;

import com.example.common.jobs.JobSubmissionGateway;
import com.example.mate.service.PartyApplicationService;
import com.example.mate.service.PaymentIntentReconciliationService;
import com.example.mate.service.PaymentIntentService;
import com.example.mate.service.PaymentTransactionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ReadOnlyPaymentEntryPointTest {

    private static final Map<Class<?>, Set<String>> ENTRY_POINTS = Map.of(
            PaymentIntentService.class, Set.of("prepareIntent", "resolveIntentForConfirm", "markConfirmed",
                    "markApplicationCreated", "compensateAfterApplicationFailure", "retryCompensation",
                    "reconcileCompensationTargets", "cancelPaymentIntent"),
            PaymentIntentReconciliationService.class, Set.of("reconcileSingleIntent", "retryCompensation"),
            PaymentTransactionService.class, Set.of("createOrGetOnConfirm", "requestManualPayout",
                    "requestSettlementOnApproval", "processCancellation"),
            PartyApplicationService.class, Set.of("createApplication", "createApplicationWithPayment",
                    "createOrGetApplicationWithPayment", "approveApplication", "rejectApplication", "cancelApplication"));

    static Stream<Method> paymentEntryPoints() {
        return ENTRY_POINTS.entrySet().stream().flatMap(entry -> Arrays.stream(entry.getKey().getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> entry.getValue().contains(method.getName()))
                // This deprecated overload always rejects authentication before any side effect.
                .filter(method -> !(method.getName().equals("createApplication") && method.getParameterCount() == 1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paymentEntryPoints")
    void rejectsRepeatedCallsBeforeRepositoriesProvidersOrAfterCommitRegistration(Method method) throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(ReadOnlyVerificationPolicy.PROFILE);
        ReadOnlyVerificationPolicy policy = new ReadOnlyVerificationPolicy(environment);
        ObjectProvider<org.jobrunr.scheduling.JobScheduler> provider = mock(ObjectProvider.class);
        JobSubmissionGateway gateway = new JobSubmissionGateway(provider, policy);
        List<Object> dependencies = new ArrayList<>();
        dependencies.add(provider);
        Constructor<?> constructor = method.getDeclaringClass().getConstructors()[0];
        Object[] constructorArguments = Arrays.stream(constructor.getParameterTypes()).map(type -> {
            if (type == ReadOnlyVerificationPolicy.class) {
                return policy;
            }
            if (type == JobSubmissionGateway.class) {
                return gateway;
            }
            Object dependency = mock(type);
            dependencies.add(dependency);
            return dependency;
        }).toArray();
        Object service = constructor.newInstance(constructorArguments);
        Object[] arguments = Arrays.stream(method.getParameterTypes())
                .map(type -> type == int.class ? 0 : null).toArray();

        TransactionSynchronizationManager.initSynchronization();
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThatThrownBy(() -> invoke(method, service, arguments))
                        .isInstanceOf(ReadOnlyVerificationUnavailableException.class);
                assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
                verifyNoInteractions(dependencies.toArray());
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static void invoke(Method method, Object service, Object[] arguments) throws Throwable {
        try {
            method.invoke(service, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
