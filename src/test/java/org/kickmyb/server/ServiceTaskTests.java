package org.kickmyb.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kickmyb.server.account.MUser;
import org.kickmyb.server.account.MUserRepository;
import org.kickmyb.server.task.MTask;
import org.kickmyb.server.task.MTaskRepository;
import org.kickmyb.server.task.ServiceTask;
import org.kickmyb.transfer.AddTaskRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.*;

// TODO pour celui ci on aimerait pouvoir mocker l'utilisateur pour ne pas avoir à le créer

// https://reflectoring.io/spring-boot-mock/#:~:text=This%20is%20easily%20done%20by,our%20controller%20can%20use%20it.

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = KickMyBServerApplication.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//@ActiveProfiles("test")
class ServiceTaskTests {

    @Autowired
    private MUserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MTaskRepository taskRepository;
    @Autowired
    private ServiceTask serviceTask;

    @Test
    void testAddTask() throws ServiceTask.Empty, ServiceTask.TooShort, ServiceTask.Existing {
        MUser u = new MUser();
        u.username = "M. Test";
        u.password = passwordEncoder.encode("Passw0rd!");
        userRepository.saveAndFlush(u);

        AddTaskRequest atr = new AddTaskRequest();
        atr.name = "Tâche de test";
        atr.deadline = Date.from(new Date().toInstant().plusSeconds(3600));

        serviceTask.addOne(atr, u);

        assertEquals(1, serviceTask.home(u.id).size());
    }

    @Test
    void testAddTaskEmpty()  {
        MUser u = new MUser();
        u.username = "M. Test";
        u.password = passwordEncoder.encode("Passw0rd!");
        userRepository.saveAndFlush(u);

        AddTaskRequest atr = new AddTaskRequest();
        atr.name = "";
        atr.deadline = Date.from(new Date().toInstant().plusSeconds(3600));

        try{
            serviceTask.addOne(atr, u);
            fail("Aurait du lancer ServiceTask.Empty");
        } catch (Exception e) {
            assertEquals(ServiceTask.Empty.class, e.getClass());
        }
    }

    @Test
    void testAddTaskTooShort() throws ServiceTask.Empty, ServiceTask.TooShort, ServiceTask.Existing {
        MUser u = new MUser();
        u.username = "M. Test";
        u.password = passwordEncoder.encode("Passw0rd!");
        userRepository.saveAndFlush(u);

        AddTaskRequest atr = new AddTaskRequest();
        atr.name = "o";
        atr.deadline = Date.from(new Date().toInstant().plusSeconds(3600));

        try{
            serviceTask.addOne(atr, u);
            fail("Aurait du lancer ServiceTask.TooShort");
        } catch (Exception e) {
            assertEquals(ServiceTask.TooShort.class, e.getClass());
        }
    }

    @Test
    void testAddTaskExisting() throws ServiceTask.Empty, ServiceTask.TooShort, ServiceTask.Existing {
        MUser u = new MUser();
        u.username = "M. Test";
        u.password = passwordEncoder.encode("Passw0rd!");
        userRepository.saveAndFlush(u);

        AddTaskRequest atr = new AddTaskRequest();
        atr.name = "Bonne tâche";
        atr.deadline = Date.from(new Date().toInstant().plusSeconds(3600));

        try{
            serviceTask.addOne(atr, u);
            serviceTask.addOne(atr, u);
            fail("Aurait du lancer ServiceTask.Existing");
        } catch (Exception e) {
            assertEquals(ServiceTask.Existing.class, e.getClass());
        }
    }
    @Test
    void testDeleteTaskWithCorrectId() {

        MUser user = new MUser();
        user.username = "Alice";
        userRepository.saveAndFlush(user);


        MTask task = new MTask();
        task.name = "Task 1";
        taskRepository.saveAndFlush(task);
        user.tasks.add(task);
        userRepository.saveAndFlush(user);


        MUser fetchedUser = userRepository.findById(user.id).get();
        assertEquals(1, fetchedUser.tasks.size());


        serviceTask.deleteTask(task.id, fetchedUser);


        MUser updatedUser = userRepository.findById(user.id).get();
        assertTrue(updatedUser.tasks.isEmpty());
        assertFalse(taskRepository.findById(task.id).isPresent());
    }

    @Test
    void testDeleteTaskWithIncorrectId() {

        MUser user = new MUser();
        user.username = "Alice";
        userRepository.saveAndFlush(user);


        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            serviceTask.deleteTask(999L, user);
        });


        assertEquals("Task not found", exception.getMessage());
    }

    @Test
    void testAccessControlForTaskDeletion() {
        // Creation de Alice
        MUser alice = new MUser();
        alice.username = "Alice";
        userRepository.saveAndFlush(alice);

        MTask task = new MTask();
        task.name = "Task 1";
        taskRepository.saveAndFlush(task);
        alice.tasks.add(task);
        userRepository.saveAndFlush(alice);

        // reprendre la tâche persistante
        MUser fetchedAlice = userRepository.findById(alice.id).get();
        assertEquals(1, fetchedAlice.tasks.size());

        // Creation de bob
        MUser bob = new MUser();
        bob.username = "Bob";
        userRepository.saveAndFlush(bob);

        // Essayer de supprimer la tâche de Alice avec Bob
        Exception exception = assertThrows(SecurityException.class, () -> {
            serviceTask.deleteTask(task.id, bob);
        });

        // Verification du message
        assertEquals("User does not own this task", exception.getMessage());
    }
}
