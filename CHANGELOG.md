# Changelog

## [0.1.20](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.19...v0.1.20) (2026-02-06)


### Bug Fixes

* npe in OpMapper ([#127](https://github.com/bzkf/obds2-to-obds3/issues/127)) ([c960e8d](https://github.com/bzkf/obds2-to-obds3/commit/c960e8d4691e132a19bbfd86b327a39f27af4085))

## [0.1.19](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.18...v0.1.19) (2026-02-06)


### Bug Fixes

* revert some changes ([#125](https://github.com/bzkf/obds2-to-obds3/issues/125)) ([bb8a7cc](https://github.com/bzkf/obds2-to-obds3/commit/bb8a7ccbb1e003aea8a711136df2b5df93d7d5f2))

## [0.1.18](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.17...v0.1.18) (2026-02-05)


### Features

* log if borderline grading and non-borderline are present ([#124](https://github.com/bzkf/obds2-to-obds3/issues/124)) ([0a57685](https://github.com/bzkf/obds2-to-obds3/commit/0a576854e79b568c21fa03da39ec501c4aacd8a4))


### Bug Fixes

* more explicit handling for multiple Diagnose.Histologie to single Histologie ([#122](https://github.com/bzkf/obds2-to-obds3/issues/122)) ([40cf043](https://github.com/bzkf/obds2-to-obds3/commit/40cf043155006fb565d56a73f48922483275bbb7))

## [0.1.17](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.16...v0.1.17) (2026-02-03)


### Bug Fixes

* npe in ST mapper and minor refactoring ([#119](https://github.com/bzkf/obds2-to-obds3/issues/119)) ([7599cb9](https://github.com/bzkf/obds2-to-obds3/commit/7599cb9c0dbaf64f328592307e20dcb3fb6d327f))

## [0.1.16](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.15...v0.1.16) (2026-01-23)


### Bug Fixes

* construct abschluss_id from patient and tumor id ([#116](https://github.com/bzkf/obds2-to-obds3/issues/116)) ([5a3290c](https://github.com/bzkf/obds2-to-obds3/commit/5a3290c9bfad2e014f7790df545f38ff175e3d61))

## [0.1.15](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.14...v0.1.15) (2026-01-21)


### Bug Fixes

* **deps:** update all non-major dependencies ([#111](https://github.com/bzkf/obds2-to-obds3/issues/111)) ([1e8857c](https://github.com/bzkf/obds2-to-obds3/commit/1e8857cb9f9c34446f31a4adaa039357e55b9988))
* **deps:** update dependency org.junit:junit-bom to v6 ([#113](https://github.com/bzkf/obds2-to-obds3/issues/113)) ([29c7106](https://github.com/bzkf/obds2-to-obds3/commit/29c7106636ee5c47aa8d839662f1fede6eecc774))
* only log if multiple entries for 'Diagnose.Histologie' are found that differ in content ([#114](https://github.com/bzkf/obds2-to-obds3/issues/114)) ([bfe6879](https://github.com/bzkf/obds2-to-obds3/commit/bfe6879765a1505f3a8f4d7fc93bd2b6c897629e))

## [0.1.14](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.13...v0.1.14) (2025-12-05)


### Bug Fixes

* support RU -&gt; U ([#105](https://github.com/bzkf/obds2-to-obds3/issues/105)) ([00caf86](https://github.com/bzkf/obds2-to-obds3/commit/00caf86c8f9004e5b2b52dfa3a50897dad09f3eb))

## [0.1.13](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.12...v0.1.13) (2025-12-04)


### Bug Fixes

* prozentzahl NPE ([#103](https://github.com/bzkf/obds2-to-obds3/issues/103)) ([0f3397b](https://github.com/bzkf/obds2-to-obds3/commit/0f3397b440980fb8130b255fe780324a8b4483ef))

## [0.1.12](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.11...v0.1.12) (2025-12-04)


### Bug Fixes

* support floating point prozentzahl ([#101](https://github.com/bzkf/obds2-to-obds3/issues/101)) ([3c301d8](https://github.com/bzkf/obds2-to-obds3/commit/3c301d85cf5eeb390d9c3f0200be53f0d0969c0f))

## [0.1.11](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.10...v0.1.11) (2025-12-03)


### Bug Fixes

* generate meldung id if missing ([#99](https://github.com/bzkf/obds2-to-obds3/issues/99)) ([35bce7b](https://github.com/bzkf/obds2-to-obds3/commit/35bce7be4045ce4c3556fbde1c3888b4d2e1ff74))

## [0.1.10](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.9...v0.1.10) (2025-12-02)


### Bug Fixes

* **deps:** update dependency org.junit:junit-bom to v6 ([#96](https://github.com/bzkf/obds2-to-obds3/issues/96)) ([bb5dc36](https://github.com/bzkf/obds2-to-obds3/commit/bb5dc368293a5a4ea1ed4c1d266e9537bb26f6a9))

## [0.1.9](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.8...v0.1.9) (2025-11-20)


### Bug Fixes

* support completely unset meldungen ([#91](https://github.com/bzkf/obds2-to-obds3/issues/91)) ([74f4567](https://github.com/bzkf/obds2-to-obds3/commit/74f45677b2f8b2099bde4d6a3c2056825d55cd80))

## [0.1.8](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.7...v0.1.8) (2025-11-20)


### Bug Fixes

* handle meldeanlass=tod without Verlauf data ([#89](https://github.com/bzkf/obds2-to-obds3/issues/89)) ([3ce2fc8](https://github.com/bzkf/obds2-to-obds3/commit/3ce2fc8bc65b3537ffb8fa1f0abcdd72166de1d1))

## [0.1.7](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.6...v0.1.7) (2025-11-20)


### Bug Fixes

* complete syst therapieart mapping ([#87](https://github.com/bzkf/obds2-to-obds3/issues/87)) ([c85b709](https://github.com/bzkf/obds2-to-obds3/commit/c85b70912721f423a03bed502c0fffbb5ffadaa6))

## [0.1.6](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.5...v0.1.6) (2025-11-19)


### Bug Fixes

* handle unset Meldebegruendung ([#85](https://github.com/bzkf/obds2-to-obds3/issues/85)) ([1be6585](https://github.com/bzkf/obds2-to-obds3/commit/1be658597d0ad14d41d7dae71b9d9b7a85c82348))

## [0.1.5](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.4...v0.1.5) (2025-11-19)


### Bug Fixes

* more lenient mapping and added TNM ID and ICDO ([#83](https://github.com/bzkf/obds2-to-obds3/issues/83)) ([5daa2b3](https://github.com/bzkf/obds2-to-obds3/commit/5daa2b3ab4ad21a42c3ab8b776813fba7d0ac19d))

## [0.1.4](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.3...v0.1.4) (2025-11-19)


### Bug Fixes

* **deps:** update dependency org.junit:junit-bom to v6 ([#79](https://github.com/bzkf/obds2-to-obds3/issues/79)) ([6e8af50](https://github.com/bzkf/obds2-to-obds3/commit/6e8af50e7a70d7c6e2de60e122bcfcd7eb7488a1))

## [0.1.3](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.2...v0.1.3) (2025-11-19)


### Bug Fixes

* **deps:** update all non-major dependencies ([#72](https://github.com/bzkf/obds2-to-obds3/issues/72)) ([31d073a](https://github.com/bzkf/obds2-to-obds3/commit/31d073ad53ebf48389e6dd672c8b26867485b54e))
* support K applikationsart ([#80](https://github.com/bzkf/obds2-to-obds3/issues/80)) ([9e22051](https://github.com/bzkf/obds2-to-obds3/commit/9e22051a02a21def88837a737290b20b2951beeb))

## [0.1.2](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.1...v0.1.2) (2025-11-19)


### Bug Fixes

* upgraded gradle and possibly fixed build ([#76](https://github.com/bzkf/obds2-to-obds3/issues/76)) ([de005af](https://github.com/bzkf/obds2-to-obds3/commit/de005af65a447ec4d4a5afe2abb8bae2d9ed622f))

## [0.1.1](https://github.com/bzkf/obds2-to-obds3/compare/v0.1.0...v0.1.1) (2025-11-19)


### Bug Fixes

* less strict handling for versichertendaten ([#59](https://github.com/bzkf/obds2-to-obds3/issues/59)) ([07d27bd](https://github.com/bzkf/obds2-to-obds3/commit/07d27bd7c7ae1e8428e9c54dfcac325fc206461b))
* use non-deprecated archive name ([#75](https://github.com/bzkf/obds2-to-obds3/issues/75)) ([2a1afa4](https://github.com/bzkf/obds2-to-obds3/commit/2a1afa47b0be45a59f52f4675a2d960991d1ad5c))
