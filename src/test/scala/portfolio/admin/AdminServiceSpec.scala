package portfolio.admin

import zio.*
import zio.test.*

object AdminServiceSpec extends ZIOSpecDefault:

  // Sender finto che registra (to, otp) di ogni invio, senza toccare la rete.
  private def trackingSender(sent: Ref[List[String]]): AdminServiceLive.EmailSender =
    (_, otp) => sent.update(otp :: _)

  def spec = suite("AdminService")(
    test("OTP valido autentica e rilascia una sessione") {
      for
        sent  <- Ref.make(List.empty[String])
        svc   <- AdminServiceLive.make(trackingSender(sent))
        _     <- svc.requestOtp
        otps  <- sent.get
        token <- svc.verifyOtp(otps.head)
        auth  <- ZIO.foreach(token)(svc.isAuthenticated)
      yield assertTrue(token.isDefined, auth.contains(true))
    },
    test("OTP errato non autentica") {
      for
        sent  <- Ref.make(List.empty[String])
        svc   <- AdminServiceLive.make(trackingSender(sent))
        _     <- svc.requestOtp
        token <- svc.verifyOtp("000000-wrong")
      yield assertTrue(token.isEmpty)
    },
    test("dopo troppi tentativi l'OTP viene invalidato anche se poi corretto") {
      for
        sent <- Ref.make(List.empty[String])
        svc  <- AdminServiceLive.make(trackingSender(sent))
        _    <- svc.requestOtp
        otps <- sent.get
        // esaurisce i tentativi con codici errati
        _     <- ZIO.foreachDiscard(1 to AdminConfig.otpMaxAttempts)(_ => svc.verifyOtp("bad"))
        token <- svc.verifyOtp(otps.head) // ora anche il codice giusto deve fallire
      yield assertTrue(token.isEmpty)
    },
    test("il cooldown evita un secondo invio ravvicinato") {
      for
        sent  <- Ref.make(List.empty[String])
        svc   <- AdminServiceLive.make(trackingSender(sent))
        _     <- svc.requestOtp
        _     <- svc.requestOtp // entro il cooldown: non deve inviare di nuovo
        count <- sent.get.map(_.size)
      yield assertTrue(count == 1)
    },
    test("logout invalida la sessione") {
      for
        sent  <- Ref.make(List.empty[String])
        svc   <- AdminServiceLive.make(trackingSender(sent))
        _     <- svc.requestOtp
        otps  <- sent.get
        token <- svc.verifyOtp(otps.head)
        t = token.get
        _       <- svc.logout(t)
        authNow <- svc.isAuthenticated(t)
      yield assertTrue(!authNow)
    }
  )
