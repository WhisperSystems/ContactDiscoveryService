package org.whispersystems.contactdiscovery.requests;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.whispersystems.contactdiscovery.enclave.NoSuchEnclaveException;
import org.whispersystems.contactdiscovery.enclave.SgxEnclave;
import org.whispersystems.contactdiscovery.entities.DiscoveryRequest;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PendingRequestQueueSetTest {

  @Test
  public void testBadEnclave() {
    SgxEnclave enclaveOne   = mock(SgxEnclave.class);
    SgxEnclave enclaveTwo   = mock(SgxEnclave.class);
    SgxEnclave enclaveThree = mock(SgxEnclave.class);

    PendingRequestQueue queueOne   = new PendingRequestQueue(enclaveOne  );
    PendingRequestQueue queueTwo   = new PendingRequestQueue(enclaveTwo  );
    PendingRequestQueue queueThree = new PendingRequestQueue(enclaveThree);

    HashMap<String, PendingRequestQueue> map = new HashMap<String, PendingRequestQueue>() {{
      put("mrenclave1", queueOne);
      put("mrenclave2", queueTwo);
      put("mrenclave3", queueThree);
    }};

    DiscoveryRequest discoveryRequestOne = mock(DiscoveryRequest.class);
    when(discoveryRequestOne.getAddressCount()).thenReturn(1000);

    PendingRequestQueueSet queue = new PendingRequestQueueSet(map);
    try {
      queue.put("nosuchenclave", discoveryRequestOne);
      throw new AssertionError("there's no such enclave");
    } catch (NoSuchEnclaveException e) {
      // good
    }
  }

  @Test
  public void testPutGet() throws NoSuchEnclaveException {
    SgxEnclave enclaveOne = mock(SgxEnclave.class);
    SgxEnclave enclaveTwo = mock(SgxEnclave.class);

    PendingRequestQueue queueOne = new PendingRequestQueue(enclaveOne);
    PendingRequestQueue queueTwo = new PendingRequestQueue(enclaveTwo);

    HashMap<String, PendingRequestQueue> map = new HashMap<String, PendingRequestQueue>() {{
      put("mrenclave1", queueOne);
      put("mrenclave2", queueTwo);
    }};

    DiscoveryRequest discoveryRequestOne = mock(DiscoveryRequest.class);
    when(discoveryRequestOne.getAddressCount()).thenReturn(1000);

    DiscoveryRequest discoveryRequestTwo = mock(DiscoveryRequest.class);
    when(discoveryRequestTwo.getAddressCount()).thenReturn(500);

    PendingRequestQueueSet queue = new PendingRequestQueueSet(map);
    queue.put("mrenclave1", discoveryRequestOne);
    queue.put("mrenclave2", discoveryRequestTwo);

    Pair<SgxEnclave, List<PendingRequest>> firstGet = queue.get(1000);

    assertEquals(firstGet.getLeft(), enclaveOne);
    assertEquals(firstGet.getRight().size(), 1);
    assertEquals(firstGet.getRight().get(0).getRequest(), discoveryRequestOne);

    Pair<SgxEnclave, List<PendingRequest>> secondGet = queue.get(10);

    assertEquals(secondGet.getLeft(), enclaveTwo);
    assertEquals(secondGet.getRight().size(), 1);
    assertEquals(secondGet.getRight().get(0).getRequest(), discoveryRequestTwo);
  }

  @Test
  public void testGetMoreThan() throws NoSuchEnclaveException {
    SgxEnclave enclaveOne = mock(SgxEnclave.class);
    SgxEnclave enclaveTwo = mock(SgxEnclave.class);

    PendingRequestQueue queueOne = new PendingRequestQueue(enclaveOne);
    PendingRequestQueue queueTwo = new PendingRequestQueue(enclaveTwo);

    HashMap<String, PendingRequestQueue> map = new HashMap<String, PendingRequestQueue>() {{
      put("mrenclave1", queueOne);
      put("mrenclave2", queueTwo);
    }};

    DiscoveryRequest discoveryRequestOne = mock(DiscoveryRequest.class);
    when(discoveryRequestOne.getAddressCount()).thenReturn(500);

    DiscoveryRequest discoveryRequestTwo = mock(DiscoveryRequest.class);
    when(discoveryRequestTwo.getAddressCount()).thenReturn(501);

    PendingRequestQueueSet queue = new PendingRequestQueueSet(map);
    queue.put("mrenclave1", discoveryRequestOne);
    queue.put("mrenclave2", discoveryRequestTwo);

    Pair<SgxEnclave, List<PendingRequest>> firstGet = queue.get(2000);

    assertEquals(firstGet.getLeft(), enclaveTwo);
    assertEquals(firstGet.getRight().size(), 1);
    assertEquals(firstGet.getRight().get(0).getRequest(), discoveryRequestTwo);

    Pair<SgxEnclave, List<PendingRequest>> secondGet = queue.get(3000);

    assertEquals(secondGet.getLeft(), enclaveOne);
    assertEquals(secondGet.getRight().size(), 1);
    assertEquals(secondGet.getRight().get(0).getRequest(), discoveryRequestOne);
  }

  @Test
  public void testGetMaxWait() throws NoSuchEnclaveException, InterruptedException {
    SgxEnclave enclaveOne = mock(SgxEnclave.class);
    SgxEnclave enclaveTwo = mock(SgxEnclave.class);

    PendingRequestQueue queueOne = new PendingRequestQueue(enclaveOne);
    PendingRequestQueue queueTwo = new PendingRequestQueue(enclaveTwo);

    HashMap<String, PendingRequestQueue> map = new HashMap<String, PendingRequestQueue>() {{
      put("mrenclave1", queueOne);
      put("mrenclave2", queueTwo);
    }};

    DiscoveryRequest discoveryRequestOne = mock(DiscoveryRequest.class);
    when(discoveryRequestOne.getAddressCount()).thenReturn(50);

    DiscoveryRequest discoveryRequestTwo = mock(DiscoveryRequest.class);
    when(discoveryRequestTwo.getAddressCount()).thenReturn(1000);

    PendingRequestQueueSet queue = new PendingRequestQueueSet(map, 500);
    queue.put("mrenclave1", discoveryRequestOne);

    Thread.sleep(501);

    queue.put("mrenclave2", discoveryRequestTwo);
    Pair<SgxEnclave, List<PendingRequest>> firstGet = queue.get(1000);

    assertEquals(firstGet.getLeft(), enclaveOne);
    assertEquals(firstGet.getRight().size(), 1);
    assertEquals(firstGet.getRight().get(0).getRequest(), discoveryRequestOne);

    Pair<SgxEnclave, List<PendingRequest>> secondGet = queue.get(3000);

    assertEquals(secondGet.getLeft(), enclaveTwo);
    assertEquals(secondGet.getRight().size(), 1);
    assertEquals(secondGet.getRight().get(0).getRequest(), discoveryRequestTwo);
  }

  @Test
  public void testBlockingGet() throws NoSuchEnclaveException, ExecutionException, InterruptedException, TimeoutException {
    SgxEnclave enclaveOne = mock(SgxEnclave.class);
    SgxEnclave enclaveTwo = mock(SgxEnclave.class);

    PendingRequestQueue queueOne = new PendingRequestQueue(enclaveOne);
    PendingRequestQueue queueTwo = new PendingRequestQueue(enclaveTwo);

    HashMap<String, PendingRequestQueue> map = new HashMap<String, PendingRequestQueue>() {{
      put("mrenclave1", queueOne);
      put("mrenclave2", queueTwo);
    }};

    DiscoveryRequest discoveryRequestOne = mock(DiscoveryRequest.class);
    when(discoveryRequestOne.getAddressCount()).thenReturn(1000);

    PendingRequestQueueSet queue = new PendingRequestQueueSet(map);

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Future<Pair<SgxEnclave, List<PendingRequest>>> future = executorService.submit(new Callable<Pair<SgxEnclave, List<PendingRequest>>>() {
      @Override
      public Pair<SgxEnclave, List<PendingRequest>> call() throws Exception {
        return queue.get(500);
      }
    });

    try {
      future.get(500, TimeUnit.MILLISECONDS);
      throw new AssertionError("Queue should block!");
    } catch (TimeoutException e) {
      // good
    }

    queue.put("mrenclave1", discoveryRequestOne);

    Pair<SgxEnclave, List<PendingRequest>> firstGet = future.get(500, TimeUnit.MILLISECONDS);

    assertEquals(firstGet.getLeft(), enclaveOne);
    assertEquals(firstGet.getRight().size(), 1);
    assertEquals(firstGet.getRight().get(0).getRequest(), discoveryRequestOne);
  }


}
