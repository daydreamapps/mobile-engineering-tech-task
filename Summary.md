# ME Tech Task Summary
Human summary of the task

## Changes Made
- Project
  - Tests added to cover missing Acceptance Criteria (failing) 
- Claude
  - [Pre-read artifact](https://claude.ai/artifact/2o7dUmeTmUVGrVubQayYkQ) (only one to be shared)
- Interview only artifacts included for reference
  - [CLAUDE.md](CLAUDE.md)
  - [PROCESS_LOG.md](PROCESS_LOG.md)
  - [process_log_assets](process_log_assets)
  - [Summary.md](Summary.md) <- this document
  - Claude: [Initial Sweep Artifact](https://claude.ai/artifact/Gv3g1rrunDjigwv7kqVix9)
  - Claude: [Fix Plan Artifact](https://claude.ai/artifact/Ac9CzT3g3ftGP19cgZnKCq)
  - Claude: [Structure Review Artifact](https://claude.ai/artifact/PYbo94H5p4hbsMDzXxxiP4)

## Findings & Next Steps
The author did not meet the task Acceptance Criteria, produced an outcome that cannot be shipped and this will require the intervention by other members of the team to meet the desired delivery date. This is an objective failure.
Next steps to be taken in the next 30 minutes (ideally)
1. Block the PR (<1 minute)
2. Push the missing tests that will fail to the branch (<5 minutes)
3. Contact author and arrange conversation to review (<5 minutes) 
4. Send summary findings/conversation agenda to author (1 minute)
5. Have conversation, stick to agenda, cover learnings and next steps (20 minutes)
6. (Conditional) wider action on code verification practices to prevent this behaviour. One-off may skip, but this must not become a trend.

### Positives
- Surface level functionality and happy-paths work
- MVVM & MVI patterns are both demonstrated to some extent
- Good patterns are shown it the project configuration & structure
- Adjustments to the code will no take long once they are understood and agreed 

### Negatives
- Secondary & edge cases in Acceptance Criteria were not covered by tests
- Tests passing was used as an alias for test coverage, hiding the gaps  
- Both MVVM and MVI are present, along with patterns that do not fit either.
- Violation of architectural separation adds debt at stage zero
- Key platform considerations were ignored e.g. Android rotation
- Changes were not tested by author for various scenarios & devices
- This was not in a fit state for another human to review. If this is a pattern then it should be 

### Lessons to take away
- Tests to cover and verify Acceptance Criteria would have caught major defects
- AI was used to implement/design, but not to review/verify. Use it to cover gaps, not gild capabilities. 
- Clear structure and practices should have been established early, before implementation, to act as context. /grill-me of similar would have saved a lot of time.
- (Leader) Investigate for signs of a wider pattern of unready work being submitted.

## Approach Taken
- **On receipt of task - initial triage**
  - Me: read through task & skip ACs  
  - Claude: clones repo, /init, run tests, read documents, initial artefact to review.
- **In 2-hour block**
  - Created LOG[PROCESS_LOG.md](PROCESS_LOG.md) for ease of review
  - Me: Re-read initial artifact, build and deploy Android & iOS
  - Me: Code scan, project structure, follow core flow, file by file. (redundant if I'm familiar, but may be needed if new area/domain)
  - Me: come up with initial ares for investigation: conformity to Acceptance Criteria, Architecture, smells (e.g. `GlobalScope`)
  - Claude: parallel investigations covering the initial areas, 3 parallel sessions.
  - Me: review complete findings, and decide next steps
  - Me: further manual testing: form factors, delete flow, rotation & empty cache
  - Me: summarise final findings confirm actions.
  - Claude: write the missing tests, confirm they fail.
  - Claude: write up pre-read summary for me & engineer
  - Me: (if not an interview) Execute Next Steps
- **Sunday evening**
  - Me: wrote up this reflection summary
  - Me: raise and review Pull request
    - Here I noticed that Claude deviated and made changes outside `/test`, changing the behaviour of the `/data` module beyond the additional failing tests I requested.
    - I'm not picking this up today, but I will flag for the activity log.
- **Monday Morning**
  - Claude: remove the rogue changes to `/main` update log
  - Me: update this, add CLAUDE.md file that was unstaged & submit


## Commentary
This was an interesting task, tool me a short while to get used to multi-platform testing but that was a small hump. I made heavy use of AI for initial triage, and once I realised that the problems were near the surface. I decided to make few changes, as the fundamental failures and possible process issues are more relevant to investigate by a leader than to actually fix them. Especially as the design patterns in use are part of that concern.

I did push the test changes, for 2 reasons: skin in the game, and a scope of change. This is now **our** record in git, and helps to limit the instinct to over-correct when criticised.

This was a fun exerciser and I look forward to discuss with the team. Samuel